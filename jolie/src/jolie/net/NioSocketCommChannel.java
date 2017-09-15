/*******************************************************************************
 *   Copyright (C) 2017 by Martin Møller Andersen <maan511@student.sdu.dk>     *
 *   Copyright (C) 2017 by Fabrizio Montesi <famontesi@gmail.com>              *
 *   Copyright (C) 2017 by Saverio Giallorenzo <saverio.giallorenzo@gmail.com> *
 *                                                                             *
 *   This program is free software; you can redistribute it and/or modify      *
 *   it under the terms of the GNU Library General Public License as           *
 *   published by the Free Software Foundation; either version 2 of the        *
 *   License, or (at your option) any later version.                           *
 *                                                                              *
 *   This program is distributed in the hope that it will be useful,           *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of            *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             *
 *   GNU General Public License for more details.                              *
 *                                                                             *
 *   You should have received a copy of the GNU Library General Public         *
 *   License along with this program; if not, write to the                     *
 *   Free Software Foundation, Inc.,                                           *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.                 *
 *                                                                             *
 *   For details about the authors of this software, see the AUTHORS file.     *
 *******************************************************************************/
package jolie.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jolie.ExecutionThread;
import jolie.net.protocols.AsyncCommProtocol;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import jolie.net.ports.InputPort;

public class NioSocketCommChannel extends StreamingCommChannel {

	public static AttributeKey<ExecutionThread> EXECUTION_CONTEXT = AttributeKey.valueOf( "ExecutionContext" );
	//public static AttributeKey<CommChannel> COMMCHANNEL = AttributeKey.valueOf( "CommChannel" );
	//public static AttributeKey<Map<Integer, CompletableFuture<Void>>> SEND_RELEASE = AttributeKey.valueOf( "SendRelease" );
	//public static AttributeKey<NioSocketListener> LISTENER = AttributeKey.valueOf( "Listener" );

	private Bootstrap bootstrap;
	private static final int SO_LINGER = 10000;
	protected CompletableFuture<CommMessage> waitingForMsg = null;
	protected final NioSocketCommChannelHandler nioSocketCommChannelHandler;
	private ChannelPipeline channelPipeline;

	public NioSocketCommChannel( URI location, AsyncCommProtocol protocol ) {
		super( location, protocol );
		this.nioSocketCommChannelHandler = new NioSocketCommChannelHandler( this );
	}
	
	public NioSocketCommChannelHandler getChannelHandler() {
		return nioSocketCommChannelHandler;
	}
	
	private void setChannelPipeline( ChannelPipeline channelPipeline ){
		this.channelPipeline = channelPipeline;
	}
	
	public ChannelPipeline getChannelPipeline(){
		return channelPipeline;
	}

	public static NioSocketCommChannel createChannel( URI location, AsyncCommProtocol protocol, EventLoopGroup workerGroup ) {
		ExecutionThread ethread = ExecutionThread.currentThread();
		NioSocketCommChannel channel = new NioSocketCommChannel( location, protocol );
		channel.bootstrap = new Bootstrap();
		channel.bootstrap.group( workerGroup )
			.channel( NioSocketChannel.class )
			.option( ChannelOption.SO_LINGER, SO_LINGER )
			.handler( new ChannelInitializer() {
				@Override
				protected void initChannel( Channel ch ) throws Exception {
					ChannelPipeline p = ch.pipeline();
					channel.setChannelPipeline( p );
					protocol.setupPipeline( p );
					p.addLast( channel.nioSocketCommChannelHandler );
					ch.attr( EXECUTION_CONTEXT ).set( ethread );
				}
			}
			);
		return channel;
	}

	public static NioSocketCommChannel createInputChannel( URI location, AsyncCommProtocol protocol, EventLoopGroup workerGroup, InputPort port, ChannelDuplexHandler dh ) {
//		URI broker = URI.create( inputPort().protocolConfigurationPath().getValue().getFirstChild( "broker" ).strValue() );
		ExecutionThread ethread = ExecutionThread.currentThread();
		NioSocketCommChannel channel = new NioSocketCommChannel( location, protocol );
		channel.setParentInputPort( port );
		channel.bootstrap = new Bootstrap();
		channel.bootstrap.group( workerGroup )
			.channel( NioSocketChannel.class )
			.option( ChannelOption.SO_LINGER, SO_LINGER )
			.handler( new ChannelInitializer() {
				@Override
				protected void initChannel( Channel ch ) throws Exception {
					ChannelPipeline p = ch.pipeline();
					channel.setChannelPipeline( p );
					protocol.setupPipeline( p );
					if ( dh != null ) {
						p.replace( "INPUT", dh.toString(), dh );
					}
					p.addLast( channel.nioSocketCommChannelHandler );
					ch.attr( EXECUTION_CONTEXT ).set( ethread );
				}
			}
			);
		return channel;
	}

	protected ChannelFuture connect( URI location ) throws InterruptedException {
		return bootstrap
			.connect( new InetSocketAddress( location.getHost(), location.getPort() ) );
	}

	@Override
	protected CommMessage recvImpl() throws IOException {
		// This is blocking to integrate with existing CommCore and ExecutionThreads.
		try {
			if ( waitingForMsg != null ) {
				throw new UnsupportedOperationException( "Waiting for multiple messages is currently not supported!" );
			}
			waitingForMsg = new CompletableFuture<>();
			CommMessage msg = waitingForMsg.get();
			waitingForMsg = null;
			return msg;
		} catch ( InterruptedException | ExecutionException ex ) {
			Logger.getLogger( NioSocketCommChannel.class.getName() ).log( Level.SEVERE, null, ex );
		}
		return null;
	}

	protected void completeRead( CommMessage message ) {
		while ( waitingForMsg == null ) {
			// spinlock
		}
		if ( waitingForMsg == null ) {
			throw new IllegalStateException( "No pending read to complete!" );
		} else {
			waitingForMsg.complete( message );
		}
	}

	@Override
	protected void sendImpl( CommMessage message ) throws IOException {
		try {
			nioSocketCommChannelHandler.write( message ).sync();
		} catch ( InterruptedException ex ) {
			throw new IOException( ex );
		}
	}

	@Override
	protected void closeImpl() throws IOException {
		try {
			nioSocketCommChannelHandler.close().sync();
		} catch ( InterruptedException ex ) {
			throw new IOException( ex );
		}
	}

}
