package com.chrisgrou.mytube

/**
 * JavaScript injected into m.youtube.com. It does two independent things:
 *
 * (Getting to Settings used to be attempted here too — three different ways of
 * injecting a "MyTube" row/bar into the page, all confirmed working against a
 * captured DOM snapshot, none of them rendering anything on the real device.
 * Given up on: MainActivity now has a plain native button instead, which
 * doesn't depend on YouTube's markup at all.)
 *
 * 1. Blocks ads, in the two ways request-level blocking (AdBlocker.kt) can't:
 *    strips the ad fields out of the /youtubei/v1/player response before
 *    YouTube's own code reads it (that's what removes pre-roll/mid-roll ads,
 *    whose media comes from the same hosts as real video), and hides sponsored
 *    items in the feed. The player patching depends on this script running at
 *    document-start, before any page script.
 *
 * 2. Forces a preferred playback quality (Settings), by calling the player's own
 *    `setPlaybackQuality`/`setPlaybackQualityRange` — the same IFrame Player API
 *    method YouTube's own embeds use, confirmed exposed on the mobile web player
 *    too (`#movie_player`, class `ytp-mweb-player`, in a real captured watch-page
 *    DOM). Applied once per video (on its `loadedmetadata`), so a manual change
 *    the user makes afterward for that video isn't fought.
 *
 * 3. Hides community "posts" that show images instead of a video — never touches
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

  function isAdBlockEnabled() {
    try { return window.MyTubeNative.isAdBlockEnabled(); } catch (e) { return true; }
  }

  // ---------------------------------------------------------------------------
  // Background audio: never let the page find out it was backgrounded.
  //
  // PlaybackService.kt keeps the *process* alive once the app is backgrounded —
  // necessary but not sufficient. YouTube's own player watches the Page
  // Visibility API and pauses the video the moment the tab is reported hidden,
  // the same way most video sites do to save battery. So document.hidden/
  // visibilityState are pinned to "visible", and the visibility/lifecycle events
  // are swallowed before any YouTube listener sees them — same trick already
  // proven for a similar problem in the no-algo-fb project.
  //
  // This works despite running after YouTube's own scripts start listening
  // because of *where* it intercepts: capture phase on window, which the DOM
  // walks before anything registered on document (where visibilitychange
  // actually dispatches, and where YouTube's own listener lives) ever sees the
  // event. Capture order beats registration order here.
  // ---------------------------------------------------------------------------
  function pinVisible(prop, value) {
    try {
      Object.defineProperty(document, prop, { configurable: true, get: function() { return value; } });
    } catch (e) {}
  }
  pinVisible('hidden', false);
  pinVisible('webkitHidden', false);
  pinVisible('visibilityState', 'visible');
  pinVisible('webkitVisibilityState', 'visible');

  var SUPPRESSED_LIFECYCLE_EVENTS = [
    'visibilitychange', 'webkitvisibilitychange', 'pagehide', 'pageshow', 'freeze', 'resume'
  ];

  function suppressLifecycleEvent(event) {
    event.stopImmediatePropagation();
    event.stopPropagation();
  }

  for (var li = 0; li < SUPPRESSED_LIFECYCLE_EVENTS.length; li++) {
    window.addEventListener(SUPPRESSED_LIFECYCLE_EVENTS[li], suppressLifecycleEvent, true);
  }

  // Also stop new listeners for those events from being registered at all —
  // covers window-targeted events a capturing window listener can't get ahead
  // of (it IS the window listener). Every other event type passes through.
  var nativeAddEventListener = EventTarget.prototype.addEventListener;
  EventTarget.prototype.addEventListener = function(type, listener, options) {
    if (listener !== suppressLifecycleEvent && SUPPRESSED_LIFECYCLE_EVENTS.indexOf(type) >= 0) {
      return;
    }
    return nativeAddEventListener.call(this, type, listener, options);
  };

  // ---------------------------------------------------------------------------
  // In-stream (pre-roll / mid-roll) ad removal.
  //
  // These ads can't be blocked by URL: their media comes from the same
  // googlevideo.com hosts as the real video, and the instructions to play them
  // ride inside the ordinary /youtubei/v1/player response. So instead we strip
  // the ad fields out of that response before YouTube's own code ever reads it.
  // This only works because the whole script is injected at document-start
  // (WebViewCompat.addDocumentStartJavaScript), i.e. before any page script runs.
  // ---------------------------------------------------------------------------
  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];

  function stripAds(obj) {
    if (!obj || typeof obj !== 'object') return obj;
    for (var i = 0; i < AD_KEYS.length; i++) {
      if (AD_KEYS[i] in obj) {
        try { delete obj[AD_KEYS[i]]; } catch (e) {}
      }
    }
    if (obj.playerResponse) stripAds(obj.playerResponse);
    if (obj.player_response) stripAds(obj.player_response);
    return obj;
  }

  // Responses parsed in JS (XHR, inline JSON).
  var origParse = JSON.parse;
  JSON.parse = function() {
    var out = origParse.apply(this, arguments);
    if (isAdBlockEnabled()) stripAds(out);
    return out;
  };

  // Responses read via fetch().json() — that's native and never goes through
  // the JSON.parse above, so it needs its own patch.
  if (window.Response && Response.prototype && Response.prototype.json) {
    var origJson = Response.prototype.json;
    Response.prototype.json = function() {
      return origJson.apply(this, arguments).then(function(data) {
        if (isAdBlockEnabled()) stripAds(data);
        return data;
      });
    };
  }

  // The very first player response is assigned as a plain object literal by an
  // inline script, so neither patch above sees it — intercept the assignment.
  try {
    var initialPlayerResponse;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function() { return initialPlayerResponse; },
      set: function(value) {
        initialPlayerResponse = isAdBlockEnabled() ? stripAds(value) : value;
      }
    });
  } catch (e) {}

  // Last-resort fallback for an ad that still starts playing: burn through it.
  // Gated on YouTube's own ad-playing markers so a real video is never scrubbed.
  var AD_PLAYING_SELECTOR = '.ad-showing, .ytp-ad-player-overlay, .ytp-ad-module, .ytmAdBadge';
  var AD_SKIP_SELECTOR = '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytmAdsSkipButton, ' +
    '[aria-label*="Skip ad"], [aria-label*="Παράλειψη"]';

  function skipVideoAd() {
    if (!isAdBlockEnabled()) return;
    if (!document.querySelector(AD_PLAYING_SELECTOR)) return;

    var skip = document.querySelector(AD_SKIP_SELECTOR);
    if (skip) { skip.click(); return; }

    var video = document.querySelector('video');
    if (video && isFinite(video.duration) && video.duration > 0) {
      try { video.currentTime = video.duration; } catch (e) {}
    }
  }

  // ---------------------------------------------------------------------------
  // Display ads in the feed / search results. Ad renderers change names often, so
  // rather than relying only on tag names this also looks for YouTube's own
  // "Sponsored" badge inside a feed item and hides the whole item. Each item is
  // scanned once (data-mytube-adscan) to keep this cheap on a long feed.
  // ---------------------------------------------------------------------------
  var AD_TAG_SELECTOR = 'ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer, ' +
    'ytm-promoted-sparkles-text-search-renderer, ytm-companion-slot-renderer, ' +
    'ytm-action-companion-ad-renderer, ytm-display-ad-renderer, ytm-carousel-ad-renderer, ' +
    'ytm-search-pyv-renderer, ad-slot-renderer, ytm-ad-slot-renderer, ytd-ad-slot-renderer';
  var AD_BADGES = ['sponsored', 'ad', 'ads', 'διαφήμιση', 'χορηγούμενο', 'χορηγειται'];
  var FEED_ITEM_SELECTOR = 'ytm-rich-item-renderer, ytm-rich-section-renderer, ytm-item-section-renderer, ytm-video-with-context-renderer';

  function hideElement(el) {
    el.style.setProperty('display', 'none', 'important');
    el.setAttribute('data-mytube-ad-hidden', '1');
  }

  function hideAds() {
    if (!isAdBlockEnabled()) return;

    var byTag = document.querySelectorAll(AD_TAG_SELECTOR);
    for (var i = 0; i < byTag.length; i++) {
      hideElement(byTag[i].closest(FEED_ITEM_SELECTOR) || byTag[i]);
    }

    var items = document.querySelectorAll(FEED_ITEM_SELECTOR);
    for (var j = 0; j < items.length; j++) {
      var item = items[j];
      if (item.getAttribute('data-mytube-adscan') === '1') continue;
      item.setAttribute('data-mytube-adscan', '1');

      var labels = item.querySelectorAll('span, div[role="text"]');
      var limit = labels.length < 40 ? labels.length : 40;
      for (var k = 0; k < limit; k++) {
        var text = (labels[k].textContent || '').trim().toLowerCase();
        if (text.length <= 14 && AD_BADGES.indexOf(text) !== -1) {
          hideElement(item);
          break;
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Preferred playback quality.
  // ---------------------------------------------------------------------------
  function getPreferredQuality() {
    try { return window.MyTubeNative.getPreferredVideoQuality(); } catch (e) { return 'auto'; }
  }

  function applyPreferredQuality(videoEl) {
    var quality = getPreferredQuality();
    if (!quality || quality === 'auto') return;
    if (videoEl && videoEl.getAttribute('data-mytube-quality-applied') === quality) return;

    var player = document.getElementById('movie_player');
    if (!player) return;
    try {
      if (typeof player.setPlaybackQualityRange === 'function') {
        player.setPlaybackQualityRange(quality, quality);
      }
      if (typeof player.setPlaybackQuality === 'function') {
        player.setPlaybackQuality(quality);
      }
      if (videoEl) videoEl.setAttribute('data-mytube-quality-applied', quality);
    } catch (e) {
      console.error('MyTube applyPreferredQuality failed', e);
    }
  }

  document.addEventListener('loadedmetadata', function(e) { applyPreferredQuality(e.target); }, true);
  document.addEventListener('playing', function(e) { applyPreferredQuality(e.target); }, true);

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

  function tick() {
    try { applyFilter(); } catch (e) { console.error('MyTube applyFilter failed', e); }
    try { hideAds(); } catch (e) { console.error('MyTube hideAds failed', e); }
    try { skipVideoAd(); } catch (e) { console.error('MyTube skipVideoAd failed', e); }
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

  // A WebView doesn't keep the screen awake during playback the way a browser
  // does, so the display dims and eventually sleeps mid-video. Report playback
  // state and let native hold FLAG_KEEP_SCREEN_ON only while something plays.
  // Media events don't bubble, so these listen in the capture phase — that still
  // reaches document, and it covers <video> elements created later.
  function reportVideoPlaying(playing) {
    try { window.MyTubeNative.setVideoPlaying(playing); } catch (e) {}
  }
  document.addEventListener('playing', function() { reportVideoPlaying(true); }, true);
  document.addEventListener('play', function() { reportVideoPlaying(true); }, true);
  document.addEventListener('pause', function() { reportVideoPlaying(false); }, true);
  document.addEventListener('ended', function() { reportVideoPlaying(false); }, true);

  // Report the video's shape so native only forces landscape on entering
  // fullscreen when the video is actually landscape — a portrait video (or a
  // Short) has to stay portrait.
  function reportVideoAspect(video) {
    if (!video || !video.videoWidth || !video.videoHeight) return;
    try { window.MyTubeNative.setVideoAspect(video.videoWidth, video.videoHeight); } catch (e) {}
  }
  document.addEventListener('loadedmetadata', function(e) { reportVideoAspect(e.target); }, true);
  document.addEventListener('playing', function(e) { reportVideoAspect(e.target); }, true);
  document.addEventListener('resize', function(e) { reportVideoAspect(e.target); }, true);

  // Queried by native right after fullscreen starts, to confirm the reported
  // shape belonged to the video actually being watched. Prefers a video that is
  // playing, then the largest one; returns null if nothing has dimensions yet.
  window.__mytubeVideoIsPortrait = function() {
    var videos = document.querySelectorAll('video');
    var best = null;
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      if (!v.videoWidth || !v.videoHeight) continue;
      if (best === null) { best = v; continue; }
      var betterState = !v.paused && best.paused;
      var worseState = v.paused && !best.paused;
      var bigger = (v.videoWidth * v.videoHeight) > (best.videoWidth * best.videoHeight);
      if (betterState || (!worseState && bigger)) best = v;
    }
    if (!best) return null;
    return best.videoHeight > best.videoWidth;
  };

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
