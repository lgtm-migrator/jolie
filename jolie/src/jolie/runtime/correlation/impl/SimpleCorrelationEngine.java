/***************************************************************************
 *   Copyright (C) 2011 by Fabrizio Montesi <famontesi@gmail.com>          *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package jolie.runtime.correlation.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jolie.Interpreter;
import jolie.SessionThread;
import jolie.lang.Constants.ExecutionMode;
import jolie.net.CommChannel;
import jolie.net.CommMessage;
import jolie.net.SessionMessage;
import jolie.runtime.FaultException;
import jolie.runtime.Value;
import jolie.runtime.correlation.CorrelationEngine;
import jolie.runtime.correlation.CorrelationSet;
import jolie.runtime.correlation.CorrelationSet.CorrelationPair;

/**
 * A simple correlation algorithm that performs a sequential check
 * of each running session every time a new message arrives
 * or a session changes one of its correlation values.
 * @author Fabrizio Montesi
 */
public class SimpleCorrelationEngine extends CorrelationEngine
{
	private final Set< SessionThread > sessions = new HashSet< SessionThread >();

	public SimpleCorrelationEngine( Interpreter interpreter )
	{
		super( interpreter );
	}

	public synchronized boolean routeMessage( CommMessage message, CommChannel channel )
	{
		for( SessionThread session : sessions ) {
			if ( correlate( session, message ) ) {
				session.pushMessage( new SessionMessage( message, channel ) );
				return true;
			}
		}
		return false;
	}

	public synchronized void onSessionStart( SessionThread session, Interpreter.SessionStarter starter, CommMessage message )
	{
		sessions.add( session );
		initCorrelationValues( session, starter, message );
	}

	public synchronized void onSingleExecutionSessionStart( SessionThread session )
	{
		sessions.add( session );
	}

	public synchronized void onSessionExecuted( SessionThread session )
	{
		sessions.remove( session );
	}

	public synchronized void onSessionError( SessionThread session, FaultException fault )
	{
		onSessionExecuted( session );
	}

	private boolean correlate( SessionThread session, CommMessage message )
	{
		if ( interpreter().correlationSets().isEmpty() && interpreter().executionMode() == ExecutionMode.SINGLE ) {
			return true;
		}

		Value sessionValue;
		Value messageValue;
		List< CorrelationPair > pairs;
		CorrelationSet cset = interpreter().getCorrelationSetForOperation( message.operationName() );
		if ( cset == null ) {
			// This should never happen!
			assert false;
			return false;
		}
		pairs = cset.getOperationCorrelationPairs( message.operationName() );
		for( CorrelationPair cpair : pairs ) {
			sessionValue = cpair.sessionPath().getValueOrNull( session.state().root() );
			if ( sessionValue == null ) {
				return false;
			} else {
				messageValue = cpair.messagePath().getValueOrNull( message.value() );
				if ( messageValue == null ) {
					return false;
				} else {
					// TODO: Value.equals is type insensitive, fix this with an additional check.
					if ( !sessionValue.isDefined() || !messageValue.isDefined() || !sessionValue.equals( messageValue ) ) {
						return false;
					}
				}
			}
		}

		return true;
	}
}
