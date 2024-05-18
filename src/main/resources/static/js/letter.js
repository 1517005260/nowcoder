$(function(){
	$("#sendBtn").click(send_letter);
	$(".close-message").click(delete_msg);
	$(".close-notice").click(delete_notice);
});

function send_letter() {
	$("#sendModal").modal("hide");

	let toName = $("#recipient-name").val();
	let content = $("#message-text").val();

	$.post(
		CONTEXT_PATH + "/letter/send",
		{"toName":toName, "content":content},
		function (data){
			data = $.parseJSON(data);
			if(data.code == 0){
				$("#hintBody").text("发送成功！");
			}else{
				$("#hintBody").text(data.message);
			}
		}
	)

	$("#hintModal").modal("show");
	setTimeout(function(){
		$("#hintModal").modal("hide");
		$("#recipient-name").val("");
		$("#message-text").val("");
		location.reload();
	}, 2000);
}

function delete_msg() {
	let btn = this;
	let id = $(btn).prev().val();

	$.post(
		CONTEXT_PATH + "/letter/delete",
		{"id": id},
		function (data) {
			data = $.parseJSON(data);
			if (data.code === 0) {
				$(btn).parents(".media").remove();
			} else {
				alert(data.msg);
			}
		}
	);
}

function delete_notice() {
	let btn = this;
	let id = $(btn).prev().val();

	$.post(
		CONTEXT_PATH + "/notice/delete",
		{"id": id},
		function (data) {
			data = $.parseJSON(data);
			if (data.code === 0) {
				$(btn).parents(".media").remove();
			} else {
				alert(data.msg);
			}
		}
	);
}