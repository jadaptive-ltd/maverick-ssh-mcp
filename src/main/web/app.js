// ============================================
// MAVERICK SSH MCP - Application Logic
// ============================================

document.addEventListener('DOMContentLoaded', () => {

  // ---- Particles ----
  const particlesEl = document.getElementById('particles');
  for (let i = 0; i < 40; i++) {
    const p = document.createElement('div');
    p.classList.add('particle-dot');
    p.style.left = Math.random() * 100 + '%';
    p.style.animationDuration = (8 + Math.random() * 12) + 's';
    p.style.animationDelay = Math.random() * 10 + 's';
    p.style.width = (1 + Math.random() * 2) + 'px';
    p.style.height = p.style.width;
    p.style.background = Math.random() > 0.5 ? '#00ff88' : '#00d4ff';
    particlesEl.appendChild(p);
  }

  // ---- Navbar scroll effect ----
  const navbar = document.getElementById('navbar');
  window.addEventListener('scroll', () => {
    if (window.scrollY > 40) {
      navbar.classList.add('scrolled');
    } else {
      navbar.classList.remove('scrolled');
    }
  });

  // ---- Terminal typing effect ----
  const heroTypeText = document.getElementById('heroTypeText');
  const phrases = [
    'ls -la /var/log',
    'cat /etc/ssh/sshd_config',
    'sftp --get remote-file.tar.gz',
    'tunnel --local 8080:app-server:3000',
    'uds --forward /run/app.sock',
    'sftp --put backup.sql prod-db-01:/backups/',
    'tunnel --remote :443:internal-api:8443',
  ];

  let phraseIndex = 0;
  let charIndex = 0;
  let isDeleting = false;
  let typeSpeed = 60;

  function typeEffect() {
    const currentPhrase = phrases[phraseIndex];

    if (!isDeleting) {
      heroTypeText.textContent = currentPhrase.substring(0, charIndex + 1);
      charIndex++;

      if (charIndex === currentPhrase.length) {
        isDeleting = true;
        typeSpeed = 2000; // Pause before deleting
      } else {
        typeSpeed = 60 + Math.random() * 40;
      }
    } else {
      heroTypeText.textContent = currentPhrase.substring(0, charIndex - 1);
      charIndex--;

      if (charIndex === 0) {
        isDeleting = false;
        phraseIndex = (phraseIndex + 1) % phrases.length;
        typeSpeed = 400; // Pause before typing next
      } else {
        typeSpeed = 30;
      }
    }

    setTimeout(typeEffect, typeSpeed);
  }

  setTimeout(typeEffect, 2000);

  // ---- Card glow follow mouse ----
  document.querySelectorAll('.feature-card').forEach(card => {
    const glow = card.querySelector('.card-glow');
    if (!glow) return;

    card.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const x = ((e.clientX - rect.left) / rect.width) * 100;
      const y = ((e.clientY - rect.top) / rect.height) * 100;
      glow.style.setProperty('--mouse-x', x + '%');
      glow.style.setProperty('--mouse-y', y + '%');
    });
  });

  // ---- Scroll reveal animations ----
  const revealElements = document.querySelectorAll('.feature-card, .arch-layer, .capability-item');

  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry, index) => {
      if (entry.isIntersecting) {
        setTimeout(() => {
          entry.target.style.opacity = '1';
          entry.target.style.transform = 'translateY(0)';
        }, index * 100);
      }
    });
  }, { threshold: 0.1 });

  revealElements.forEach(el => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(30px)';
    el.style.transition = 'all 0.6s cubic-bezier(0.16, 1, 0.3, 1)';
    observer.observe(el);
  });

  // ---- Smooth scroll for nav links ----
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function(e) {
      const targetId = this.getAttribute('href');
      if (targetId === '#') return;

      e.preventDefault();
      const target = document.querySelector(targetId);
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  });

  // ---- Copy install command ----
  const copyBtn = document.getElementById('copyInstallBtn');
  const installCmd = document.getElementById('installCmd');
  const installOutput = document.getElementById('installOutput');

  if (copyBtn && installCmd) {
    copyBtn.addEventListener('click', () => {
      const cmd = installCmd.textContent;
      navigator.clipboard.writeText(cmd).then(() => {
        const originalHTML = copyBtn.innerHTML;
        copyBtn.innerHTML = `
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#00ff88" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
          Copied!
        `;
        copyBtn.style.borderColor = 'rgba(0, 255, 136, 0.3)';

        if (installOutput) {
          installOutput.style.display = 'block';
        }

        setTimeout(() => {
          copyBtn.innerHTML = originalHTML;
          copyBtn.style.borderColor = '';
        }, 2000);
      });
    });
  }

  // ---- Parallax floating icons ----
  const floatIcons = document.querySelectorAll('.float-icon');
  window.addEventListener('scroll', () => {
    const scrollY = window.scrollY;
    floatIcons.forEach((icon, index) => {
      const speed = 0.1 + (index * 0.05);
      icon.style.transform = `translateY(${scrollY * speed}px)`;
    });
  });

  // ---- Tilt effect on cards ----
  document.querySelectorAll('[data-tilt]').forEach(card => {
    card.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const centerX = rect.width / 2;
      const centerY = rect.height / 2;
      const rotateX = ((y - centerY) / centerY) * -3;
      const rotateY = ((x - centerX) / centerX) * 3;

      card.style.transform = `translateY(-4px) perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
    });

    card.addEventListener('mouseleave', () => {
      card.style.transform = '';
    });
  });

});
