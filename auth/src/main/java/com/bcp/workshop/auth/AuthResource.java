package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String MESSAGE_FIELD = "message";
    private static final String EXCEPTION_FIELD = "exception";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";
    private static final String INTERNAL_AUTHENTICATION_ERROR_MESSAGE = "Internal authentication error";
    private static final String INTERNAL_SERVER_ERROR_EXCEPTION = "InternalServerError";
    private static final Logger LOGGER = Logger.getLogger(AuthResource.class.getName());

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            return Response.ok(authService.login(request)).build();
        } catch (AuthService.InvalidLoginRequestException error) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(MESSAGE_FIELD, error.getMessage()))
                    .build();
        } catch (AuthService.InvalidCredentialsException error) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of(MESSAGE_FIELD, INVALID_CREDENTIALS_MESSAGE))
                    .build();
        } catch (Exception error) {
            LOGGER.log(Level.SEVERE, "Unexpected error during authentication", error);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of(
                    MESSAGE_FIELD, INTERNAL_AUTHENTICATION_ERROR_MESSAGE,
                    EXCEPTION_FIELD, INTERNAL_SERVER_ERROR_EXCEPTION
            )).build();
        }
    }
}
