package com.heroku.api;

import com.google.inject.Provides;
import com.heroku.api.command.login.BasicAuthLogin;
import com.heroku.api.connection.AsyncHttpClientConnection;
import com.heroku.api.exception.RequestFailedException;

import java.io.IOException;


public class AsyncHttpClientModule extends ConnectionTestModule<AsyncHttpClientConnection> {
    @Provides()
    AsyncHttpClientConnection getConnectionImpl() throws IOException {
        AuthenticationTestCredentials cred = getCredentials();
        try {
            return new AsyncHttpClientConnection(new BasicAuthLogin(cred.username, cred.password, cred.endpoint));
        } catch (RequestFailedException e) {
            System.out.println(e.getMessage());
            System.out.println(e.getStatusCode());
            System.out.println(e.getResponseBody());
            System.out.flush();
            e.printStackTrace();
            throw e;
        }
    }
}