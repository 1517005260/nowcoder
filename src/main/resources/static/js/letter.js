$(function(){
	$("#sendBtn").click(send_letter);
	$(".close").click(delete_msg);
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
	// TODO 删除数据
	$(this).parents(".media").remove();
}