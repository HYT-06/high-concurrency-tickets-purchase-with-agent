document.addEventListener('DOMContentLoaded', function () {
    // 鉴权检查
    if (!Auth.requireAuth()) return;

    var user = Auth.getUser();
    document.getElementById('welcomeText').textContent = '欢迎, ' + (user.userName || '用户');

    var trainId = sessionStorage.getItem('currentTrainId');
    var trainName = sessionStorage.getItem('currentTrainName');
    if (!trainId) { window.location.href = '/trains.html'; return; }

    document.getElementById('trainNameBreadcrumb').textContent = trainName || '座位详情';
    document.getElementById('trainTitle').textContent = (trainName || '车次') + ' - 座位选择';

    var selectedSeatId = null;
    var msgBox = document.getElementById('msgBox');
    var bookBtn = document.getElementById('bookBtn');

    loadSeats(trainId);

    function loadSeats(trainId) {
        selectedSeatId = null;
        bookBtn.disabled = true;
        bookBtn.textContent = '请选择座位';
        Auth.authFetch('/seat/train/' + trainId)
            .then(function (res) { return res.json(); })
            .then(function (seats) {
                var grid = document.getElementById('seatGrid');
                if (!seats || seats.length === 0) {
                    grid.innerHTML = '<div style="text-align:center;padding:32px;width:100%;color:#6a6a7a;">该车次暂无座位数据</div>';
                    return;
                }
                grid.innerHTML = seats.map(function (s) {
                    var available = s.status === 'available';
                    var cls = available ? 'available' : 'booked';
                    return '<div class="seat ' + cls + '" data-id="' + s.id + '" data-available="' + available + '" onclick="toggleSeat(this, event)">' +
                        '<span>' + (s.seatNumber || s.id) + '</span>' +
                        '<span class="seat-type-tag">' + (s.status || '') + '</span>' +
                        '</div>';
                }).join('');
            })
            .catch(function () {
                document.getElementById('seatGrid').innerHTML =
                    '<div style="text-align:center;padding:32px;width:100%;color:#ff6666;">座位加载失败</div>';
            });
    }

    window.toggleSeat = function (el, ev) {
        if (el.dataset.available !== 'true') return;
        // 取消之前的选择
        var prev = document.querySelector('.seat.selected');
        if (prev && prev !== el) {
            prev.classList.remove('selected');
        }
        el.classList.toggle('selected');
        if (el.classList.contains('selected')) {
            selectedSeatId = el.dataset.id;
            bookBtn.disabled = false;
            bookBtn.textContent = '预订座位 ' + (el.querySelector('span').textContent);
        } else {
            selectedSeatId = null;
            bookBtn.disabled = true;
            bookBtn.textContent = '请选择座位';
        }
    };

    window.bookSeat = function () {
        if (!selectedSeatId) return;

        var userId = Auth.getUser().id;
        msgBox.className = 'msg-box';
        msgBox.style.display = 'none';
        bookBtn.disabled = true;
        bookBtn.textContent = '预订中...';

        Auth.authFetch('/order/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                userId: userId,
                trainId: Number(trainId),
                seatId: Number(selectedSeatId)
            })
        })
        .then(function (res) { return res.json(); })
        .then(function (data) {
            if (data.success) {
                msgBox.className = 'msg-box success';
                msgBox.innerHTML = '预订成功！<a href="/orders.html" style="color:#e94560;margin-left:8px;">查看订单 →</a>';
                msgBox.style.display = 'block';
                loadSeats(trainId);
            } else {
                msgBox.className = 'msg-box error';
                msgBox.textContent = data.message || '预订失败';
                msgBox.style.display = 'block';
                bookBtn.disabled = false;
                bookBtn.textContent = '请选择座位';
            }
        })
        .catch(function () {
            msgBox.className = 'msg-box error';
            msgBox.textContent = '网络错误，请重试';
            msgBox.style.display = 'block';
            bookBtn.disabled = false;
            bookBtn.textContent = '请选择座位';
        });
    };
});
