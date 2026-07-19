document.addEventListener('DOMContentLoaded', function () {
    // 鉴权检查
    if (!Auth.requireAuth()) return;

    var user = Auth.getUser();
    document.getElementById('welcomeText').textContent = '欢迎, ' + (user.userName || '用户');

    loadTrains();

    function loadTrains() {
        // 使用 authFetch 自动带 Authorization 头
        Auth.authFetch('/train/list')
            .then(function (res) { return res.json(); })
            .then(function (trains) {
                var tbody = document.getElementById('trainList');
                if (!trains || trains.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:32px;color:#6a6a7a;">暂无车次数据</td></tr>';
                    return;
                }
                tbody.innerHTML = trains.map(function (t) {
                    return '<tr class="clickable" onclick="viewSeats(' + t.id + ', \'' + escapeHtml(t.name) + '\')">' +
                        '<td><strong>' + escapeHtml(t.name) + '</strong></td>' +
                        '<td>' + escapeHtml(t.number || '-') + '</td>' +
                        '<td>' + escapeHtml(t.type || '-') + '</td>' +
                        '<td>' + escapeHtml(t.departure || '-') + '</td>' +
                        '<td>' + escapeHtml(t.arrival || '-') + '</td>' +
                        '<td>' + escapeHtml(t.duration || '-') + '</td>' +
                        '<td style="color:#e94560;">¥' + (t.price || 0) + '</td>' +
                        '<td>' + escapeHtml(t.status || '-') + '</td>' +
                        '<td><button class="btn btn-sm" onclick="event.stopPropagation(); viewSeats(' + t.id + ', \'' + escapeHtml(t.name) + '\')">选座</button></td>' +
                        '</tr>';
                }).join('');
            })
            .catch(function () {
                document.getElementById('trainList').innerHTML =
                    '<tr><td colspan="9" style="text-align:center;padding:32px;color:#ff6666;">加载失败，请确保服务已启动</td></tr>';
            });
    }
});

function viewSeats(trainId, trainName) {
    sessionStorage.setItem('currentTrainId', trainId);
    sessionStorage.setItem('currentTrainName', trainName);
    window.location.href = '/seats.html';
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
