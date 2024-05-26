$(function() {
    $("#publishBtn").click(publish);
    $("#backIndexBtn").click(backIndex);
});

function publish() {
    // 获取标题和内容
    var title = $("#recipient-name").val().trim();
    var content = testEditor.getMarkdown().trim(); // 修改这里获取编辑器内容

    if (title === "") {
        showHintModal("标题不能为空");
        return;
    }

    if (content === "") {
        showHintModal("内容不能为空");
        return;
    }

    // 隐藏发布模态框
    $("#publishModal").modal("hide");

    let token = $("meta[name= '_csrf']").attr("content");
    let header = $("meta[name= '_csrf_header']").attr("content");
    $(document).ajaxSend(function (e, xhr, options) {
        xhr.setRequestHeader(header, token);
    });

    // 发送异步请求
    $.post(
        CONTEXT_PATH + "/discuss/add",
        {"title": title, "content": content},
        function (data) {
            data = $.parseJSON(data);
            // 在提示框 hintBody 显示服务端返回的消息
            $("#hintBody").text("发布成功！");
            // 显示提示框
            $("#hintModal").modal("show");
            // 2s 后自动隐藏提示框
            setTimeout(function() {
                $("#hintModal").modal("hide");
                // 操作完成后，跳转到首页
                if (data.code == 0) {
                    location.href = CONTEXT_PATH + "/index";
                }
            }, 2000);
        }
    );
}

function showHintModal(message) {
    $("#hintBody").text(message);
    $("#hintModal").modal("show");
    setTimeout(function() {
        $("#hintModal").modal("hide");
    }, 2000); // 2s 后自动隐藏提示框
}

function backIndex() {
    location.href = CONTEXT_PATH + "/index";
}