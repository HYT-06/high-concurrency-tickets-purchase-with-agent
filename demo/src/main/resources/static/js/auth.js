/**
 * 认证工具模块 —— Token 管理 + 鉴权 fetch
 *
 * 用法：
 *   - authFetch(url, options)  → 自动带 Authorization 头的 fetch
 *   - getUser()                → 当前登录用户
 *   - getToken()               → JWT token 字符串
 *   - requireAuth()            → 未登录则跳转登录页
 *   - logout()                 → 清除登录态并跳转登录页
 */

var Auth = (function () {
    var TOKEN_KEY = 'jwt_token';
    var USER_KEY = 'current_user';

    /** 获取 JWT token */
    function getToken() {
        return sessionStorage.getItem(TOKEN_KEY);
    }

    /** 保存 token */
    function setToken(token) {
        if (token) {
            sessionStorage.setItem(TOKEN_KEY, token);
        } else {
            sessionStorage.removeItem(TOKEN_KEY);
        }
    }

    /** 获取当前用户 */
    function getUser() {
        var raw = sessionStorage.getItem(USER_KEY);
        if (!raw) return null;
        try {
            return JSON.parse(raw);
        } catch (e) {
            return null;
        }
    }

    /** 保存用户信息 */
    function setUser(user) {
        if (user) {
            // 不要把 token 存两份 —— token 单独存
            var u = Object.assign({}, user);
            delete u.token;
            sessionStorage.setItem(USER_KEY, JSON.stringify(u));
        } else {
            sessionStorage.removeItem(USER_KEY);
        }
    }

    /**
     * 带鉴权的 fetch 封装
     * 自动添加 Authorization: Bearer <token> 头
     */
    function authFetch(url, options) {
        options = options || {};
        var headers = options.headers || {};

        // 如果是对象，复制一份以免修改调用方传入的对象
        if (!(headers instanceof Headers)) {
            headers = Object.assign({}, headers);
        }

        var token = getToken();
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }
        options.headers = headers;

        return fetch(url, options);
    }

    /** 确保已登录，否则跳转登录页 */
    function requireAuth() {
        if (!getToken() || !getUser()) {
            window.location.href = '/login.html';
            return false;
        }
        return true;
    }

    /** 退出登录 */
    function logout() {
        sessionStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(USER_KEY);
        sessionStorage.removeItem('currentTrainId');
        sessionStorage.removeItem('currentTrainName');
        window.location.href = '/login.html';
    }

    /** 登录成功后保存 token 和用户信息 */
    function handleLoginSuccess(user) {
        setToken(user.token);
        setUser(user);
    }

    // 公开 API
    return {
        getToken: getToken,
        setToken: setToken,
        getUser: getUser,
        setUser: setUser,
        authFetch: authFetch,
        requireAuth: requireAuth,
        logout: logout,
        handleLoginSuccess: handleLoginSuccess
    };
})();
