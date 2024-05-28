// window.onload()  用于给按钮绑定事件
$(function(){
    $("#topBtn").click(setTop);
    $("#wonderfulBtn").click(setWonderful);
    $("#deleteBtn").click(setDelete);
    $("#shareBtn").click(share);
});


function like(btn, entityType, entityId, entityUserId, postId){
    let token = $("meta[name= '_csrf']").attr("content");
    let header = $("meta[name= '_csrf_header']").attr("content");
    $(document).ajaxSend(function (e, xhr, options){
        xhr.setRequestHeader(header, token);
    });

    $.post(
        CONTEXT_PATH + "/like",
        {"entityType":entityType, "entityId":entityId, "entityUserId":entityUserId, "postId":postId},
        function (data){
            data = $.parseJSON(data);
            if(data.code == 0){
                $(btn).children("i").text(data.likeCount);
                $(btn).children("b").text(data.likeStatus == 1 ? "已赞" : "赞");
            }else {
                alert("你还未登录哦！请登录后再点赞！");
            }
        }
    )
}

function setTop(){
    let token = $("meta[name= '_csrf']").attr("content");
    let header = $("meta[name= '_csrf_header']").attr("content");
    $(document).ajaxSend(function (e, xhr, options){
        xhr.setRequestHeader(header, token);
    });

    $.post(
        CONTEXT_PATH + "/discuss/top",
        {"id":$("#postId").val()},
        function (data){
            data = $.parseJSON(data);
            if(data.code == 0){
                $("#topBtn").text(data.type === 1 ? '取消置顶' : '置顶');
                location.reload();
            }else{
                alert(data.msg);
            }
        }
    );
}

function setWonderful(){
    let token = $("meta[name= '_csrf']").attr("content");
    let header = $("meta[name= '_csrf_header']").attr("content");
    $(document).ajaxSend(function (e, xhr, options){
        xhr.setRequestHeader(header, token);
    });

    $.post(
        CONTEXT_PATH + "/discuss/wonderful",
        {"id":$("#postId").val()},
        function (data){
            data = $.parseJSON(data);
            if(data.code == 0){
                $("#wonderfulBtn").text(data.status == 1 ? '取消加精' : '加精');
                location.reload();
            }else{
                alert(data.msg);
            }
        }
    );
}

function setDelete(){
    let token = $("meta[name= '_csrf']").attr("content");
    let header = $("meta[name= '_csrf_header']").attr("content");
    $(document).ajaxSend(function (e, xhr, options){
        xhr.setRequestHeader(header, token);
    });

    $.post(
        CONTEXT_PATH + "/discuss/delete",
        {"id":$("#postId").val()},
        function (data){
            data = $.parseJSON(data);
            if(data.code == 0){
               // 删除成功后帖子就没了
                location.href = CONTEXT_PATH + "/index";
            }else{
                alert(data.msg);
            }
        }
    );
}

var titleText="";
var authorText = "";
document.addEventListener("DOMContentLoaded", function() {
        // 获取包含ID为"title"的元素
        var title = document.getElementById("title");

        // 获取元素的文本值
        titleText = title.innerText; // 或者使用 textContent

        var author = document.getElementById("author")
        authorText = author.innerText;
});
function share(){
    let currentUrl = window.location.href;
    // 组织要复制的内容
    const formattedText = `${titleText} - ${authorText}的帖子 - 校园论坛\n${currentUrl}`;

    // 使用Clipboard API复制格式化后的内容到剪切板
    navigator.clipboard.writeText(formattedText).then(() => {
        // 显示自定义提示框
        const customAlert = document.getElementById('customAlert');
        customAlert.style.display = 'block';

        // 1秒后自动隐藏提示框
        setTimeout(() => {
            customAlert.style.display = 'none';
        }, 1000);
    }).catch(err => {
        // 复制失败时提示用户
        alert('复制失败: ' + err);
    });
}

function update(id){
    window.location.href = CONTEXT_PATH + "/discuss/updatePost/" + id;
}