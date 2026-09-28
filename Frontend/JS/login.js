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
        function handleLogin(e) {
            e.preventDefault();

            const userId = document.getElementById('loginUser').value;
            const password = document.getElementById('loginPassword').value;
            const errorMsg = document.getElementById('loginError');
            const btn = document.getElementById('loginBtn');
            const btnText = document.getElementById('loginBtnText');

            errorMsg.classList.remove('show');
            btn.disabled = true;
            btnText.textContent = 'Logging in...';

            // Simulate API call
            setTimeout(() => {
                // Check if user exists in localStorage or use demo credentials
                const users = JSON.parse(localStorage.getItem('trafficBuddyUsers') || '{}');
                const demoUser = userId === 'admin' && password === '123';
                const registeredUser = users[userId] && users[userId].password === password;

                if (demoUser || registeredUser) {
                    btnText.textContent = 'Success! ✓';
                    document.querySelector('.auth-container').classList.add('success-animation');
                    
                    // Store login session
                    localStorage.setItem('trafficBuddyLoggedIn', 'true');
                    localStorage.setItem('trafficBuddyCurrentUser', userId);

                    setTimeout(() => {
                        window.location.href = 'dashboard.html';
                    }, 1000);
                } else {
                    errorMsg.classList.add('show');
                    btn.disabled = false;
                    btnText.textContent = 'Login to Dashboard';
                }
            }, 1500);
        }

        // Handle Signup
        function handleSignup(e) {
            e.preventDefault();

            const name = document.getElementById('signupName').value;
            const email = document.getElementById('signupEmail').value;
            const password = document.getElementById('signupPassword').value;
            const confirmPassword = document.getElementById('signupConfirmPassword').value;
            const errorMsg = document.getElementById('signupError');
            const btn = document.getElementById('signupBtn');
            const btnText = document.getElementById('signupBtnText');

            errorMsg.classList.remove('show');

            // Validate passwords match
            if (password !== confirmPassword) {
                errorMsg.textContent = 'Passwords do not match.';
                errorMsg.classList.add('show');
                return;
            }

            btn.disabled = true;
            btnText.textContent = 'Creating account...';

            // Simulate API call
            setTimeout(() => {
                // Store user data
                const users = JSON.parse(localStorage.getItem('trafficBuddyUsers') || '{}');
                
                // Check if user already exists
                if (users[email]) {
                    errorMsg.textContent = 'Email already registered. Please login.';
                    errorMsg.classList.add('show');
                    btn.disabled = false;
                    btnText.textContent = 'Create Account';
                    return;
                }

                // Register new user
                users[email] = {
                    name: name,
                    email: email,
                    password: password,
                    createdAt: new Date().toISOString()
                };

                localStorage.setItem('trafficBuddyUsers', JSON.stringify(users));
                
                btnText.textContent = 'Account Created! ✓';
                document.querySelector('.auth-container').classList.add('success-animation');

                setTimeout(() => {
                    // Auto login after signup
                    localStorage.setItem('trafficBuddyLoggedIn', 'true');
                    localStorage.setItem('trafficBuddyCurrentUser', email);
                    window.location.href = 'dashboard.html';
                }, 1500);
            }, 1500);
        }

        // Check if already logged in
        if (localStorage.getItem('trafficBuddyLoggedIn') === 'true') {
            window.location.href = 'dashboard.html';
        }