/**
 * 登录页面逻辑
 */
document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('loginForm');
    var nameInput = document.getElementById('name');
    var passwordInput = document.getElementById('password');
    var togglePwd = document.getElementById('togglePwd');
    var submitBtn = document.getElementById('submitBtn');
    var msgBox = document.getElementById('msgBox');

    // 已登录则直接跳转
    if (Auth.getToken() && Auth.getUser()) {
        window.location.href = '/trains.html';
        return;
    }

    // 密码显示/隐藏
    togglePwd.addEventListener('click', function () {
        if (passwordInput.type === 'password') {
            passwordInput.type = 'text';
            togglePwd.textContent = '🙈';
        } else {
            passwordInput.type = 'password';
            togglePwd.textContent = '👁';
        }
    });

    // 清除单个字段错误
    nameInput.addEventListener('input', function () {
        nameInput.classList.remove('error');
        document.getElementById('nameError').classList.remove('show');
    });
    passwordInput.addEventListener('input', function () {
        passwordInput.classList.remove('error');
        document.getElementById('pwdError').classList.remove('show');
    });

    // 提交登录
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        msgBox.className = 'msg-box';
        msgBox.style.display = 'none';

        // 前端校验
        var valid = true;
        var name = nameInput.value.trim();
        var password = passwordInput.value;

        if (!name) {
            nameInput.classList.add('error');
            document.getElementById('nameError').classList.add('show');
            valid = false;
        }
        if (!password) {
            passwordInput.classList.add('error');
            document.getElementById('pwdError').classList.add('show');
            valid = false;
        }
        if (!valid) return;

        // 发送请求（login 在白名单中，不需要 token）
        submitBtn.classList.add('loading');
        submitBtn.disabled = true;

        fetch('/user/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: name, password: password })
        })
        .then(function (res) {
            if (!res.ok) throw new Error('服务器错误: ' + res.status);
            return res.json();
        })
        .then(function (data) {
            submitBtn.classList.remove('loading');
            submitBtn.disabled = false;

            if (data.success) {
                // 使用 Auth 模块保存 token 和用户信息
                Auth.handleLoginSuccess(data.user);
                window.location.href = '/trains.html';
            } else {
                msgBox.className = 'msg-box error';
                msgBox.textContent = data.message || '用户名或密码错误';
                msgBox.style.display = 'block';
            }
        })
        .catch(function () {
            submitBtn.classList.remove('loading');
            submitBtn.disabled = false;
            msgBox.className = 'msg-box error';
            msgBox.textContent = '网络错误，请检查服务是否启动';
            msgBox.style.display = 'block';
        });
    });
});
