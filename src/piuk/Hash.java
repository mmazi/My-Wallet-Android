/*
 * Copyright 2011-2012 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package piuk;

import java.io.Serializable;
import java.util.Arrays;

import org.apache.commons.lang.ArrayUtils;

import com.google.bitcoin.bouncycastle.util.encoders.Hex;

public class Hash implements Serializable {
	private static final long serialVersionUID = 1L;
	private final byte[] hash;

	public Hash() { this.hash = null; }
	public Hash(String hex) {
		this.hash = Hex.decode(hex);
	}
	
	public Hash(byte[] bytes) {
		this.hash = bytes;
	}
	
	public void reverse() {
		ArrayUtils.reverse(hash);
	}

	public byte[] getBytes() {
		return hash;
	}

	public int nLeadingZeros() {
		int n = 0;
		
		for (byte b : hash) {
			if (b == 0) 
				n += 8;
			else {
				n += Math.max(0, Integer.numberOfLeadingZeros(b) - (3*8));				
				break;
			}
		}

		return n;
	}
	
	public boolean isNull() {
		if (hash == null || hash.length == 0)
			return true;

		for (byte b : hash) {
			if (b != 0) return false;
		}

		return true;
	}
	
	public String toString() {
		
		if (hash == null)
			return null;
		
		return new String(Hex.encode(hash));
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(hash);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Hash other = (Hash) obj;
		if (!Arrays.equals(hash, other.hash))
			return false;
		return true;
	}
}
