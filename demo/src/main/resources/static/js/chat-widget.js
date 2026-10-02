/**
 * 智能客服浮动组件
 *
 * 用法：页面引入 auth.js + chat-widget.js 后自动初始化。
 * 依赖 Auth.getUser() 获取当前用户 ID 作为会话标识。
 */
var ChatWidget = (function () {
    var AGENT_URL = '/chat';
    var DEBUG = true;
    var panel = null;
    var messagesEl = null;
    var inputEl = null;
    var sendBtn = null;
    var typingEl = null;

    function log(tag, msg, data) {
        if (!DEBUG) return;
        var args = ['[ChatWidget] ' + tag + ' ' + msg];
        if (data !== undefined) args.push(data);
        console.log.apply(console, args);
    }

    function init() {
        log('INIT', '开始初始化...');
        log('INIT', '当前页面 URL: ' + window.location.href);
        log('INIT', 'Auth.getUser():', Auth.getUser());
        log('INIT', 'Auth.getToken():', Auth.getToken() ? '(存在)' : '(无)');

        if (!Auth.getUser()) {
            log('INIT', '未登录，跳过初始化');
            return;
        }
        log('INIT', '已登录，创建DOM');
        createDOM();
        bindEvents();
        log('INIT', '初始化完成，浮动按钮已注入');
    }

    function createDOM() {
        var btn = document.createElement('button');
        btn.className = 'chat-float-btn';
        btn.innerHTML = '💬';
        btn.title = '智能客服';
        btn.id = 'chatFloatBtn';
        document.body.appendChild(btn);

        panel = document.createElement('div');
        panel.className = 'chat-panel';
        panel.id = 'chatPanel';
        panel.innerHTML =
            '<div class="chat-header">' +
            '  <span class="title">🤖 智能客服</span>' +
            '  <button class="close-btn" id="chatCloseBtn">&times;</button>' +
            '</div>' +
            '<div class="chat-messages" id="chatMessages">' +
            '  <div class="chat-bubble agent">你好！我是票务预订系统的智能客服，有什么可以帮助你的吗？</div>' +
            '</div>' +
            '<div class="chat-input-area">' +
            '  <input type="text" id="chatInput" placeholder="输入你的问题..." maxlength="500">' +
            '  <button class="send-btn" id="chatSendBtn">发送</button>' +
            '</div>';
        document.body.appendChild(panel);

        messagesEl = document.getElementById('chatMessages');
        inputEl = document.getElementById('chatInput');
        sendBtn = document.getElementById('chatSendBtn');
    }

    function bindEvents() {
        document.getElementById('chatFloatBtn').addEventListener('click', toggle);
        document.getElementById('chatCloseBtn').addEventListener('click', close);
        sendBtn.addEventListener('click', handleSend);
        inputEl.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                handleSend();
            }
        });
    }

    function toggle() {
        panel.classList.toggle('open');
        if (panel.classList.contains('open')) {
            inputEl.focus();
        }
    }

    function close() {
        panel.classList.remove('open');
    }

    // 每个标签页一个会话标识，存在 sessionStorage 里（关掉标签页即失效）。
    // 用途是隔离对话记忆：此前所有会话共用 userId 作记忆 key，
    // 同一用户开多个标签页会互相看到对方的上下文。
    function getSessionId() {
        var key = 'chat_session_id';
        var sid = sessionStorage.getItem(key);
        if (!sid) {
            sid = (window.crypto && crypto.randomUUID)
                ? crypto.randomUUID()
                : String(Date.now()) + '-' + Math.random().toString(36).slice(2);
            sessionStorage.setItem(key, sid);
        }
        return sid;
    }

    function handleSend() {
        var text = inputEl.value.trim();
        if (!text) return;
        if (sendBtn.disabled) return;

        appendBubble('user', text);
        inputEl.value = '';
        showTyping();
        sendBtn.disabled = true;

        var headers = { 'Content-Type': 'application/json' };
        var token = Auth.getToken();
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        // 身份不再由前端上传：网关校验 JWT 后会注入 X-User-Id，
        // agent 只认这个头，请求体里的 userId 即使传了也会被忽略。
        // sessionId 每个标签页独立，用于隔离对话记忆（避免多标签页共用同一份上下文）。
        var body = { sessionId: getSessionId(), message: text };
        var requestUrl = AGENT_URL;

        log('SEND', '=== 发送请求 ===');
        log('SEND', 'URL: ' + requestUrl);
        log('SEND', 'Method: POST');
        log('SEND', 'Headers:', headers);
        log('SEND', 'Body:', body);
        log('SEND', '完整绝对 URL: ' + (new URL(requestUrl, window.location.href)).href);

        fetch(requestUrl, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(body)
        })
            .then(function (res) {
                log('RESPONSE', '=== 收到响应 ===');
                log('RESPONSE', 'Status: ' + res.status + ' ' + res.statusText);
                log('RESPONSE', 'Content-Type: ' + res.headers.get('content-type'));
                log('RESPONSE', 'OK: ' + res.ok);

                // 克隆一份用来读 raw text 调试
                var cloned = res.clone();
                return cloned.text().then(function (rawText) {
                    log('RESPONSE', 'Raw body (前500字符): ' + rawText.substring(0, 500));
                    // 返回原始 response 用于后续 .json() 解析
                    return res;
                });
            })
            .then(function (res) {
                return res.json();
            })
            .then(function (data) {
                log('PARSE', 'JSON 解析成功:', data);
                hideTyping();
                sendBtn.disabled = false;
                if (data.success) {
                    appendBubble('agent', data.reply);
                } else {
                    log('PARSE', 'success=false, error=' + data.error);
                    appendBubble('error', data.error || '服务暂时不可用，请稍后重试');
                }
            })
            .catch(function (err) {
                log('ERROR', '=== 请求失败 ===');
                log('ERROR', '错误类型: ' + err.name);
                log('ERROR', '错误消息: ' + err.message);
                log('ERROR', '完整错误:', err);
                hideTyping();
                sendBtn.disabled = false;
                appendBubble('error', '无法连接客服服务，请确认服务已启动\n[Debug] ' + err.message);
            });
    }

    function appendBubble(type, text) {
        var el = document.createElement('div');
        el.className = 'chat-bubble ' + type;
        el.textContent = text;
        messagesEl.appendChild(el);
        scrollToBottom();
    }

    function showTyping() {
        typingEl = document.createElement('div');
        typingEl.className = 'chat-typing';
        typingEl.innerHTML = '<div class="dot"></div><div class="dot"></div><div class="dot"></div>';
        messagesEl.appendChild(typingEl);
        scrollToBottom();
    }

    function hideTyping() {
        if (typingEl) {
            typingEl.remove();
            typingEl = null;
        }
    }

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    return {
        toggle: toggle,
        close: close
    };
})();
