// DMRS Download Website - Main JavaScript

document.addEventListener('DOMContentLoaded', function() {

    // Navbar Scroll
    var navbar = document.querySelector('.navbar');
    var backToTop = document.getElementById('backToTop');

    if (navbar) {
        window.addEventListener('scroll', function() {
            if (window.scrollY > 50) {
                navbar.classList.add('scrolled');
            } else {
                navbar.classList.remove('scrolled');
            }
        });
    }

    if (backToTop) {
        window.addEventListener('scroll', function() {
            if (window.scrollY > 400) {
                backToTop.classList.add('visible');
            } else {
                backToTop.classList.remove('visible');
            }
        });

        backToTop.addEventListener('click', function() {
            window.scrollTo(0, 0);
        });
    }

    // Mobile Menu
    var mobileToggle = document.getElementById('mobileToggle');
    var navLinks = document.querySelector('.nav-links');

    if (mobileToggle && navLinks) {
        mobileToggle.addEventListener('click', function() {
            navLinks.classList.toggle('active');
        });

        var links = navLinks.querySelectorAll('a');
        for (var i = 0; i < links.length; i++) {
            links[i].addEventListener('click', function() {
                navLinks.classList.remove('active');
            });
        }
    }

    // Video Player
    var videoPlaceholder = document.getElementById('videoPlaceholder');
    var videoIframe = document.getElementById('videoIframe');
    var youtubeFrame = document.getElementById('youtubeFrame');
    var videoWrapper = document.getElementById('videoWrapper');
    var YOUTUBE_VIDEO_ID = '';

    if (videoPlaceholder && videoIframe) {
        videoPlaceholder.addEventListener('click', function() {
            if (YOUTUBE_VIDEO_ID && youtubeFrame) {
                youtubeFrame.src = 'https://www.youtube.com/embed/' + YOUTUBE_VIDEO_ID + '?autoplay=1';
                videoPlaceholder.style.display = 'none';
                videoIframe.style.display = 'block';
            } else if (videoWrapper) {
                var msg = document.createElement('div');
                msg.style.cssText = 'position:absolute;top:0;left:0;right:0;bottom:0;display:flex;align-items:center;justify-content:center;background:rgba(18,18,42,0.95);color:white;font-size:1.1rem;text-align:center;padding:20px;border-radius:16px;z-index:10;';
                msg.innerHTML = '<div><i class="fas fa-video" style="font-size:3rem;display:block;margin-bottom:16px;color:#6c5ce7;"></i>Video coming soon!<br><small style="color:#999;">Tutorial video will be uploaded here.</small></div>';
                videoWrapper.appendChild(msg);
            }
        });
    }

    // QR Code
    var qrCodeEl = document.getElementById('qrCode');
    var downloadQR = document.getElementById('downloadQR');
    var isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);

    if (isMobile && downloadQR) {
        downloadQR.style.display = 'none';
    }

    if (qrCodeEl && !isMobile) {
        var qrImg = document.createElement('img');
        qrImg.src = 'https://api.qrserver.com/v1/create-qr-code/?size=140x140&data=' + encodeURIComponent(window.location.href) + '&bgcolor=ffffff&color=6c5ce7';
        qrImg.alt = 'QR Code';
        qrImg.style.width = '100%';
        qrImg.style.height = '100%';
        qrImg.style.borderRadius = '8px';
        qrCodeEl.appendChild(qrImg);
    }

    // Scroll Animations
    var fadeElements = document.querySelectorAll('.feature-card, .info-card, .step-item, .download-card');
    for (var j = 0; j < fadeElements.length; j++) {
        fadeElements[j].classList.add('fade-in', 'hidden');
    }

    if ('IntersectionObserver' in window) {
        var observer = new IntersectionObserver(function(entries) {
            for (var k = 0; k < entries.length; k++) {
                if (entries[k].isIntersecting) {
                    entries[k].target.classList.remove('hidden');
                    entries[k].target.classList.add('visible');
                }
            }
        }, { threshold: 0.1 });

        for (var m = 0; m < fadeElements.length; m++) {
            observer.observe(fadeElements[m]);
        }
    } else {
        for (var n = 0; n < fadeElements.length; n++) {
            fadeElements[n].classList.remove('hidden');
            fadeElements[n].classList.add('visible');
        }
    }

    // Smooth Scroll
    var anchors = document.querySelectorAll('a[href^="#"]');
    for (var p = 0; p < anchors.length; p++) {
        anchors[p].addEventListener('click', function(e) {
            var href = this.getAttribute('href');
            if (href === '#') return;
            var target = document.querySelector(href);
            if (target) {
                e.preventDefault();
                var top = target.getBoundingClientRect().top + window.pageYOffset - 80;
                window.scrollTo(0, top);
            }
        });
    }

});