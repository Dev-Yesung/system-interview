package load_balancer;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;

public class ProxyResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

	private final ChannelHandlerContext clientContext;

	public ProxyResponseHandler(ChannelHandlerContext clientContext) {
		this.clientContext = clientContext;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext context, FullHttpResponse response) {
		FullHttpResponse copiedResponse = response.copy();
		clientContext.writeAndFlush(copiedResponse).addListener(ChannelFutureListener.CLOSE);
	}
}
