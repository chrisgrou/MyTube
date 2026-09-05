package com.chrisgrou.mytube

/**
 * JavaScript injected into m.youtube.com. It does two independent things:
 *
 * 1. Adds a settings (cog) button next to the YouTube logo in the header, styled
 *    to look like a native part of the page. If the exact header markup can't be
 *    found (YouTube changes its DOM often) it falls back to a floating button in
 *    the same visual spot, so the entry point to Settings never disappears.
 *
 * 2. Hides community "posts" that show images instead of a video — never touches
 *    normal video items or the Shorts shelf, since those use different element
 *    tags. Runs on a MutationObserver + interval because m.youtube.com is a
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

  var POST_SELECTOR = 'ytm-backstage-post-renderer, ytm-post-renderer, ytd-backstage-post-renderer, ytm-shared-post-renderer, [class*="backstage-post"]';
  var ITEM_WRAPPER_SELECTOR = 'ytm-rich-item-renderer, ytm-item-section-renderer > div, ytd-rich-item-renderer';

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

  var COG_SVG = '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">' +
    '<path d="M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 ' +
    'l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 ' +
    'h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 ' +
    'C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.04,0.64,0.09,0.94l-2.03,1.58 ' +
    'c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 ' +
    'c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 ' +
    'c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 ' +
    's1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"/></svg>';

  function makeButton() {
    var btn = document.createElement('button');
    btn.id = 'mytube-settings-btn';
    btn.setAttribute('aria-label', 'MyTube settings');
    btn.innerHTML = COG_SVG;
    btn.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;' +
      'width:36px;height:36px;margin-left:6px;border:none;border-radius:18px;' +
      'background:transparent;color:inherit;cursor:pointer;padding:0;z-index:2147483647;';
    btn.addEventListener('click', function(e) {
      e.preventDefault();
      e.stopPropagation();
      try { window.MyTubeNative.openSettings(); } catch (err) {}
    });
    return btn;
  }

  function ensureCogButton() {
    if (document.getElementById('mytube-settings-btn')) return;

    var logo = document.querySelector(
      'ytm-mobile-topbar-renderer a.topbar-menu-button-avatar-button, ' +
      'ytm-mobile-topbar-renderer .mobile-topbar-header-logo, ' +
      '.mobile-topbar-header-logo, ' +
      'ytm-mobile-topbar-renderer a[href="/"]'
    );

    if (logo && logo.parentElement) {
      var btn = makeButton();
      logo.insertAdjacentElement('afterend', btn);
      return;
    }

    // Fallback: floating button pinned near where the logo normally sits,
    // so Settings stays reachable even if YouTube's header markup changed.
    if (!document.body) return;
    var floating = makeButton();
    floating.style.position = 'fixed';
    floating.style.top = 'calc(env(safe-area-inset-top, 0px) + 8px)';
    floating.style.left = '96px';
    floating.style.background = 'rgba(15,15,15,0.55)';
    floating.style.color = '#fff';
    document.body.appendChild(floating);
  }

  function tick() {
    ensureCogButton();
    applyFilter();
  }

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
