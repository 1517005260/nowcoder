// window.onload()  用于给按钮绑定事件
$(function(){
    $("#topBtn").click(setTop);
    $("#wonderfulBtn").click(setWonderful);
    $("#deleteBtn").click(setDelete);
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
            }else{
                alert(data.msg);
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
                // 点过置顶按钮后disable
                $("#topBtn").attr("disabled", "disabled");
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
                $("#wonderfulBtn").attr("disabled", "disabled");
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