document.addEventListener('DOMContentLoaded', function () {
    // 鉴权检查
    if (!Auth.requireAuth()) return;

    var user = Auth.getUser();
    document.getElementById('welcomeText').textContent = '欢迎, ' + (user.userName || '用户');

    var userId = user.id;
    var msgBox = document.getElementById('msgBox');

    loadOrders();

    function loadOrders() {
        Auth.authFetch('/order/user/' + userId)
            .then(function (res) { return res.json(); })
            .then(function (orders) {
                var tbody = document.getElementById('orderList');
                if (!orders || orders.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:48px 32px;color:#6a6a7a;">' +
                        '<div style="font-size:40px;margin-bottom:12px;">🎫</div>' +
                        '<div style="margin-bottom:8px;">暂无订单</div>' +
                        '<a href="/trains.html" class="btn btn-sm" style="margin-top:8px;">去预订车票</a></td></tr>';
                    return;
                }

                // 并发查车次名和座位号
                var detailPromises = orders.map(function (o) {
                    return Promise.all([
                        Auth.authFetch('/train/' + o.trainId).then(function (r) { return r.json(); }).catch(function () { return {}; }),
                        Auth.authFetch('/seat/' + o.seatId).then(function (r) { return r.json(); }).catch(function () { return {}; })
                    ]).then(function (results) {
                        return {
                            order: o,
                            train: results[0],
                            seat: results[1]
                        };
                    });
                });

                return Promise.all(detailPromises);
            })
            .then(function (rows) {
                if (!rows) return;
                var tbody = document.getElementById('orderList');
                tbody.innerHTML = rows.map(function (r) {
                    var o = r.order;
                    var trainName = (r.train && r.train.name) ? r.train.name : ('#' + o.trainId);
                    var seatNum = (r.seat && r.seat.seatNumber) ? r.seat.seatNumber : ('#' + o.seatId);
                    var canCancel = o.status === 'paid' || o.status === 'pending';

                    var statusMap = {
                        'paid': { text: '已支付', cls: 'status-paid' },
                        'pending': { text: '待确认', cls: 'status-pending' },
                        'cancelled': { text: '已取消', cls: 'status-cancelled' }
                    };
                    var st = statusMap[o.status] || { text: o.status || '-', cls: '' };

                    var orderIdStr = o.id != null ? String(o.id) : '';
                    return '<tr>' +
                        '<td style="font-family:monospace;font-size:12px;">' + orderIdStr + '</td>' +
                        '<td><strong>' + escapeHtml(trainName) + '</strong></td>' +
                        '<td>' + escapeHtml(seatNum) + '</td>' +
                        '<td style="color:#e94560;font-weight:600;">¥' + (o.price || 0) + '</td>' +
                        '<td>' + (o.date || '-') + '</td>' +
                        '<td><span class="' + st.cls + '">' + st.text + '</span></td>' +
                        '<td style="font-size:12px;color:#888;">' + (o.createdAt || '-') + '</td>' +
                        '<td>' + (canCancel ? '<button class="btn btn-outline btn-sm" data-order-id="' + orderIdStr + '" onclick="cancelOrder(\'' + orderIdStr + '\')">取消</button>' : '-') + '</td>' +
                        '</tr>';
                }).join('');
            })
            .catch(function () {
                document.getElementById('orderList').innerHTML =
                    '<tr><td colspan="8" style="text-align:center;padding:32px;color:#ff6666;">加载失败，请确保服务已启动</td></tr>';
            });
    }

    window.cancelOrder = function (orderId) {
        if (!confirm('确定要取消这个订单吗？座位将重新释放。')) return;

        msgBox.className = 'msg-box';
        msgBox.style.display = 'none';

        Auth.authFetch('/order/delete/' + orderId, { method: 'DELETE' })
            .then(function (res) { return res.json(); })
            .then(function (data) {
                if (data.success) {
                    msgBox.className = 'msg-box success';
                    msgBox.textContent = '订单已取消，座位已释放';
                    msgBox.style.display = 'block';
                    loadOrders();
                } else {
                    msgBox.className = 'msg-box error';
                    msgBox.textContent = data.message || '取消失败';
                    msgBox.style.display = 'block';
                }
            })
            .catch(function () {
                msgBox.className = 'msg-box error';
                msgBox.textContent = '网络错误，请重试';
                msgBox.style.display = 'block';
            });
    };
});

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
