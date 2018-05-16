package org.fogbowcloud.manager.core.plugins.identity.exceptions;

public class InvalidCredentialsException extends UnauthorizedException {

	private static final long serialVersionUID = 1L;
	private static final String message = "Invalid Credentials";
	
	public InvalidCredentialsException() {
		super(message);
	}
	
	public InvalidCredentialsException(String message) {
		super(message);
	}
	
	public InvalidCredentialsException(String message, Throwable cause) {
		super(message, cause);
	}
}
