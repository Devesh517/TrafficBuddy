// =====================================
// TrafficBuddy — Login / Signup Logic
// =====================================

// Must match API_BASE in JS/index.js
const API_BASE = "http://localhost:8080";

// Create floating particles
const bgAnimation = document.getElementById('bgAnimation');
for (let i = 0; i < 40; i++) {
    const particle = document.createElement('div');
    particle.className = 'particle';
    particle.style.left = Math.random() * 100 + '%';
    particle.style.top = Math.random() * 100 + '%';
    particle.style.animationDelay = Math.random() * 15 + 's';
    particle.style.animationDuration = (10 + Math.random() * 10) + 's';
    bgAnimation.appendChild(particle);
}

// Lightweight toast, used instead of alert() for a look consistent with the dashboard
let toastTimeout;
function showToast(text, duration = 3500) {
    let toast = document.getElementById('toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toast';
        toast.className = 'toast';
        document.body.appendChild(toast);
    }
    clearTimeout(toastTimeout);
    toast.textContent = text;
    toast.classList.add('active');
    toastTimeout = setTimeout(() => toast.classList.remove('active'), duration);
}

function showComingSoon(e) {
    e.preventDefault();
    showToast('🔧 Password reset is coming soon.');
}

// Switch between Login and Signup
function switchTab(tab) {
    const loginForm = document.getElementById('loginForm');
    const signupForm = document.getElementById('signupForm');
    const tabButtons = document.querySelectorAll('.tab-btn');

    tabButtons.forEach(btn => btn.classList.remove('active'));

    if (tab === 'login') {
        loginForm.classList.add('active');
        signupForm.classList.remove('active');
        tabButtons[0].classList.add('active');
    } else {
        signupForm.classList.add('active');
        loginForm.classList.remove('active');
        tabButtons[1].classList.add('active');
    }

    // Clear error messages
    document.getElementById('loginError').classList.remove('show');
    document.getElementById('signupError').classList.remove('show');
}

// Handle Login
// CHANGED: this used to "log in" against localStorage / a hardcoded admin/123 check with
// no backend involved. The backend now has a real signin gate (AuthFilter + AuthService,
// demo/demo123) guarding /api/route/... and /api/chat, so this now calls that endpoint
// and stores the session token it returns. Every dashboard API call needs that token in
// an "Authorization: Bearer <token>" header - see JS/index.js.
async function handleLogin(e) {
    e.preventDefault();

    const userId = document.getElementById('loginUser').value.trim();
    const password = document.getElementById('loginPassword').value;
    const errorMsg = document.getElementById('loginError');
    const btn = document.getElementById('loginBtn');
    const btnText = document.getElementById('loginBtnText');

    errorMsg.classList.remove('show');
    btn.disabled = true;
    btnText.textContent = 'Logging in...';

    try {
        const response = await fetch(`${API_BASE}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: userId, password: password }),
        });

        if (!response.ok) {
            throw new Error('Invalid credentials. Please try again.');
        }

        const data = await response.json();
        if (!data.token) {
            throw new Error('Login succeeded but no session token was returned.');
        }

        btnText.textContent = 'Success! ✓';
        document.querySelector('.auth-container').classList.add('success-animation');

        // Store the session token + who's logged in, so index.js can send it on every
        // backend call and the dashboard can guard itself against a missing/expired session.
        localStorage.setItem('trafficBuddyToken', data.token);
        localStorage.setItem('trafficBuddyCurrentUser', userId);

        setTimeout(() => {
            window.location.href = '../HTML/index.html';
        }, 800);
    } catch (error) {
        console.error('Login error:', error);
        errorMsg.textContent = error.message && error.message.includes('fetch')
            ? 'Could not reach the server. Is the backend running?'
            : 'Invalid credentials. Please try again.';
        errorMsg.classList.add('show');
        btn.disabled = false;
        btnText.textContent = 'Login to Dashboard';
    }
}

// Handle Signup
// CHANGED: the backend is a lightweight exhibition build with a single hardcoded demo
// account (demo/demo123) and no registration endpoint, so there is no real account for
// a signup form to create. Rather than silently "succeed" into a local-only account that
// can never actually sign in against the backend, this now tells the user to use the
// demo credentials instead.
function handleSignup(e) {
    e.preventDefault();

    const password = document.getElementById('signupPassword').value;
    const confirmPassword = document.getElementById('signupConfirmPassword').value;
    const errorMsg = document.getElementById('signupError');

    errorMsg.classList.remove('show');

    if (password !== confirmPassword) {
        errorMsg.textContent = 'Passwords do not match.';
        errorMsg.classList.add('show');
        return;
    }

    showToast('🔧 Sign up isn\'t available in this demo build — please log in with demo / demo123.', 5000);
    switchTab('login');
    document.getElementById('loginUser').value = 'demo';
}

// If we already hold a (not-yet-expired-on-the-client) token, skip straight to the
// dashboard. The backend is still the source of truth: if the token has actually expired
// server-side, the first API call in index.js will get a 401 and bounce back here.
if (localStorage.getItem('trafficBuddyToken')) {
    window.location.href = '../HTML/index.html';
}