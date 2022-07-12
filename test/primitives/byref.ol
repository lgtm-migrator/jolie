/*
 * Copyright (C) 2022 Fabrizio Montesi <famontesi@gmail.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

from ..test-unit import TestUnitInterface
from .private.byref-server import ByRefServer

service Test {
	inputPort TestUnitInput {
		location: "local"
		interfaces: TestUnitInterface
	}

	embed ByRefServer as byRefServer

	main {
		test()() {
			x << {
				items[0] = "0"
				items[1] = "1"
				items[2] = "2"
			}
			y << &x
			if( is_defined( x ) )
				throw( TestFailed, "passing by reference did not undefine" )

			request.x = 1
			run@byRefServer( &request )()
			if( is_defined( request.x ) )
				throw( TestFailed, "passing by reference exposed a side-effect" )
			
			request.x = 1
			run@byRefServer( request )()
			if( request.x != 1 )
				throw( TestFailed, "passing a copy exposed a side-effect" )
		}
	}
}
