$(function(){
    $("#verifyCodeBtn").click(getVerifyCode);
});

function getVerifyCode() {
    var email = $("#your-email").val();

    if(!email) {
        alert("请先填写您的邮箱！");
        return false;
    }

    $.get(
        CONTEXT_PATH + "/forget/code",
        {"email":email},
        function(data) {
            data = $.parseJSON(data);
            if(data.code == 0) {
                alert("验证码已发送至您的邮箱,请登录邮箱查看!");
            } else {
                alert(data.msg);
            }
        }
    );
}