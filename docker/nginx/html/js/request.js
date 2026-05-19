/**
 * 全局 axios 拦截器
 * 所有页面在引入 axios 后引入本文件即可获得：
 *   - 请求拦截：自动注入 authorization header
 *   - 响应拦截：401 时清除 token 并跳转登录页
 */
(function () {
    // 请求拦截：自动注入 token
    axios.interceptors.request.use(function (config) {
        var token = localStorage.getItem('token');
        if (token) {
            config.headers['authorization'] = token;
        }
        return config;
    }, function (error) {
        return Promise.reject(error);
    });

    // 响应拦截：统一处理 401 登录过期
    axios.interceptors.response.use(function (response) {
        return response;
    }, function (error) {
        if (error.response && error.response.status === 401) {
            localStorage.removeItem('token');
            var page = window.location.pathname.split('/').pop();
            if (page !== 'login.html') {
                // 记录当前页面，登录后可跳回
                sessionStorage.setItem('redirectAfterLogin', window.location.href);
                alert('登录已过期，请重新登录');
                window.location.href = 'login.html';
            }
        }
        return Promise.reject(error);
    });
})();
