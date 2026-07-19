document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('registerForm');
    var submitBtn = document.getElementById('submitBtn');
    var msgBox = document.getElementById('msgBox');
    var fields = ['userName', 'password', 'confirmPwd', 'phone'];

    // 已登录则跳转首页
    if (Auth.getToken() && Auth.getUser()) {
        window.location.href = '/trains.html';
        return;
    }

    // 清除单个字段错误
    fields.forEach(function (id) {
        var el = document.getElementById(id);
        if (!el) return;
        el.addEventListener('input', function () {
            el.classList.remove('error');
            var errEl = document.getElementById(id + 'Error');
            if (errEl) errEl.classList.remove('show');
        });
    });

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        msgBox.className = 'msg-box';
        msgBox.style.display = 'none';

        var valid = true;
        var userName = document.getElementById('userName').value.trim();
        var password = document.getElementById('password').value;
        var confirmPwd = document.getElementById('confirmPwd').value;
        var phone = document.getElementById('phone').value.trim();
        var address = document.getElementById('address').value.trim();

        if (!userName) { showError('userName'); valid = false; }
        if (!password || password.length < 6) { showError('password'); valid = false; }
        if (password !== confirmPwd) { showError('confirmPwd'); valid = false; }
        if (!phone || !/^1[3-9]\d{9}$/.test(phone)) { showError('phone'); valid = false; }
        if (!valid) return;

        submitBtn.disabled = true;
        submitBtn.textContent = '注册中...';

        // register 在白名单中，不需要 token
        fetch('/user/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                userName: userName,
                password: password,
                phone: parseInt(phone, 10),
                address: address,
                status: 1
            })
        })
        .then(function (res) { return res.json(); })
        .then(function (data) {
            submitBtn.disabled = false;
            submitBtn.textContent = '注 册';
            if (data.code === 'OK') {
                msgBox.className = 'msg-box success';
                msgBox.textContent = '注册成功！3秒后跳转到登录页...';
                msgBox.style.display = 'block';
                setTimeout(function () { window.location.href = '/login.html'; }, 3000);
            } else {
                msgBox.className = 'msg-box error';
                msgBox.textContent = data.msg || data.message || '注册失败';
                msgBox.style.display = 'block';
            }
        })
        .catch(function () {
            submitBtn.disabled = false;
            submitBtn.textContent = '注 册';
            msgBox.className = 'msg-box error';
            msgBox.textContent = '网络错误，请检查服务是否启动';
            msgBox.style.display = 'block';
        });
    });

    function showError(id) {
        var el = document.getElementById(id);
        if (el) el.classList.add('error');
        var errEl = document.getElementById(id + 'Error');
        if (errEl) errEl.classList.add('show');
    }
});
