$(function(){
	$(".follow-btn").click(follow);
});

function follow() {
	let btn = this;
	if($(btn).hasClass("btn-info")) {
		let token = $("meta[name= '_csrf']").attr("content");
		let header = $("meta[name= '_csrf_header']").attr("content");
		$(document).ajaxSend(function (e, xhr, options){
			xhr.setRequestHeader(header, token);
		});
		// 关注TA
		$.post(
			CONTEXT_PATH + "/follow",
			{"entityType":3, "entityId":$(btn).prev().val()},
			function (data){
				data = $.parseJSON(data);
				if(data.code == 0){
					window.location.reload();
				}else{
					alert(data.msg);
				}
			}
		);
	} else {
		let token = $("meta[name= '_csrf']").attr("content");
		let header = $("meta[name= '_csrf_header']").attr("content");
		$(document).ajaxSend(function (e, xhr, options){
			xhr.setRequestHeader(header, token);
		});
		// 取消关注
		$.post(
			CONTEXT_PATH + "/unfollow",
			{"entityType":3, "entityId":$(btn).prev().val()},
			function (data){
				data = $.parseJSON(data);
				if(data.code == 0){
					window.location.reload();
				}else{
					alert(data.msg);
				}
			}
		);
	}
}