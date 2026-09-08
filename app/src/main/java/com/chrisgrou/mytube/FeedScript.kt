package com.chrisgrou.mytube

/**
 * JavaScript injected into m.youtube.com. It does two independent things:
 *
 * 1. Adds a "MyTube" row at the bottom of YouTube's own Settings page (reached
 *    via the account avatar > Settings), so opening our Settings doesn't need a
 *    floating button sitting on top of YouTube's UI. It's appended as a plain,
 *    independently-styled block right after `<ytm-settings>` inside its parent
 *    `.page-container` — NOT nested inside any of YouTube's own custom elements
 *    (`ytm-setting-generic-category` etc.), because those are web components
 *    with their own shadow-DOM render template that silently ignores/replaces
 *    light-DOM children handed to them; a first attempt building a row out of
 *    that exact tag never rendered anything for precisely that reason.
 *
 * 2. Hides community "posts" that show images instead of a video — never touches
 *    normal video items (`ytm-rich-item-renderer`) or the Shorts shelf, since
 *    those use different element tags. Confirmed against a real captured DOM
 *    (m.youtube.com, Sept 2026): a community post is
 *    `ytm-rich-section-renderer > div.rich-section-content >
 *    ytm-backstage-post-thread-renderer > ytm-backstage-post-renderer`, so hiding
 *    the `ytm-rich-section-renderer` ancestor removes the whole card cleanly
 *    (header, text, images, like/comment row) with no leftover gap.
 *    Runs on a MutationObserver + interval because m.youtube.com is a
 *    single-page app that swaps content without a full page load.
 *
 * Because this is scraping a third-party page we don't control, the selectors
 * below are best-effort and may need updating if YouTube changes its markup.
 */
object FeedScript {

    const val SCRIPT = """
(function() {
  if (window.__mytubeInstalled) return;
  window.__mytubeInstalled = true;

  function isHideEnabled() {
    try { return window.MyTubeNative.isHideImagePostsEnabled(); } catch (e) { return true; }
  }

  var POST_SELECTOR = 'ytm-backstage-post-thread-renderer, ytm-backstage-post-renderer, ytm-post-multi-image-renderer, ytm-shared-post-renderer';
  var ITEM_WRAPPER_SELECTOR = 'ytm-rich-section-renderer, ytm-rich-item-renderer, ytm-item-section-renderer > div';

  function applyFilter() {
    var hide = isHideEnabled();
    var posts = document.querySelectorAll(POST_SELECTOR);
    for (var i = 0; i < posts.length; i++) {
      var el = posts[i];
      var wrapper = el.closest(ITEM_WRAPPER_SELECTOR) || el;
      if (hide) {
        wrapper.style.setProperty('display', 'none', 'important');
        wrapper.setAttribute('data-mytube-hidden', '1');
      } else if (wrapper.getAttribute('data-mytube-hidden') === '1') {
        wrapper.style.removeProperty('display');
        wrapper.removeAttribute('data-mytube-hidden');
      }
    }
  }

  var COG_SVG = '<svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">' +
    '<path d="M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 ' +
    'l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 ' +
    'h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 ' +
    'C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.04,0.64,0.09,0.94l-2.03,1.58 ' +
    'c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 ' +
    'c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 ' +
    'c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 ' +
    's1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"/></svg>';

  function ensureSettingsMenuItem() {
    if (document.getElementById('mytube-settings-row')) return;
    var settings = document.querySelector('ytm-settings');
    if (!settings || !settings.parentElement) return;

    var row = document.createElement('div');
    row.id = 'mytube-settings-row';
    row.setAttribute('role', 'button');
    row.tabIndex = 0;
    row.style.cssText = 'display:flex;align-items:center;gap:24px;padding:16px;' +
      'cursor:pointer;color:#fff;font-family:Roboto,Arial,sans-serif;' +
      'border-top:1px solid rgba(255,255,255,0.2);';
    row.innerHTML =
      '<span style="display:flex;flex-shrink:0;">' + COG_SVG + '</span>' +
      '<span style="font-size:14px;">MyTube</span>';
    row.addEventListener('click', function(e) {
      e.preventDefault();
      e.stopPropagation();
      try { window.MyTubeNative.openSettings(); } catch (err) {}
    });
    settings.parentElement.appendChild(row);
  }

  function tick() {
    try { ensureSettingsMenuItem(); } catch (e) { console.error('MyTube ensureSettingsMenuItem failed', e); }
    try { applyFilter(); } catch (e) { console.error('MyTube applyFilter failed', e); }
  }

  // The native pull-to-refresh only knows whether the *main page* is scrolled to
  // the top — it has no idea a touch actually landed inside one of YouTube's own
  // nested scrollable panels (comments, a bottom sheet, an engagement panel), so
  // without this a downward swipe meant to scroll that panel gets eaten as a
  // page refresh instead. On every touch, walk up from the touch target (through
  // shadow DOM boundaries via composedPath) looking for a scrollable ancestor
  // that isn't the page itself; if found, tell native to disable the pull gesture
  // until the next touch starts.
  function isScrollable(el) {
    if (!el || el === document.body || el === document.documentElement) return false;
    var style = window.getComputedStyle(el);
    var overflowY = style.overflowY;
    return (overflowY === 'auto' || overflowY === 'scroll') && el.scrollHeight > el.clientHeight;
  }

  function setPullToRefreshAllowed(allowed) {
    try { window.MyTubeNative.setPullToRefreshAllowed(allowed); } catch (e) {}
  }

  document.addEventListener('touchstart', function(e) {
    var path = typeof e.composedPath === 'function' ? e.composedPath() : [e.target];
    var nestedScrollable = false;
    for (var i = 0; i < path.length; i++) {
      if (isScrollable(path[i])) {
        nestedScrollable = true;
        break;
      }
    }
    setPullToRefreshAllowed(!nestedScrollable);
  }, { capture: true, passive: true });

  document.addEventListener('touchend', function() {
    setPullToRefreshAllowed(true);
  }, { capture: true, passive: true });

  var observer = new MutationObserver(function() { tick(); });
  function startObserving() {
    if (document.body) {
      observer.observe(document.body, { childList: true, subtree: true });
      tick();
    } else {
      requestAnimationFrame(startObserving);
    }
  }
  startObserving();

  document.addEventListener('yt-navigate-finish', tick);
  setInterval(tick, 1500);

  window.__mytubeApplyFilter = applyFilter;
})();
"""
}
