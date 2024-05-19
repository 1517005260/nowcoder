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