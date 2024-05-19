$(function(){
	$("#publishBtn").click(publish);  //点击发布按钮时，调用发布方法
});

function publish() {
	$("#publishModal").modal("hide");  //发布的页面隐藏

	//获取标题和内容
	let title = $("#recipient-name").val();
	let content = $("#message-text").val();

	// 发送AJAX前，带上csrf令牌
	let token = $("meta[name= '_csrf']").attr("content");
	let header = $("meta[name= '_csrf_header']").attr("content");
	$(document).ajaxSend(function (e, xhr, options){
		xhr.setRequestHeader(header, token);
	});

	//ajax
	$.post(
		CONTEXT_PATH + "/discuss/add",
		{"title":title, "content":content},
		function (data){
			data = $.parseJSON(data);
			// 在提示框中显示返回消息
			$("#hintBody").text(data.message);
			// 显示提示框
			$("#hintModal").modal("show");
			// 2s后自动隐藏
			setTimeout(function(){
				$("#hintModal").modal("hide");
				//刷新页面
				if(data.code == 0){
					window.location.reload();
				}
				//清空输入框内容
				$("#recipient-name").val("");
				$("#message-text").val("");
			}, 2000);
		}
	)
}