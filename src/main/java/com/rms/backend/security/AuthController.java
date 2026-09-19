package com.rms.backend.security;

import com.rms.backend.common.ApiPaths;
import com.rms.backend.common.SecurityConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping(ApiPaths.AUTH)
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping(ApiPaths.REGISTER)
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.Token register(@Valid @RequestBody AuthRequests.CreateAccount dto) { return auth.register(dto); }

    @PostMapping(ApiPaths.LOGIN)
    public AuthService.Token login(@Valid @RequestBody AuthRequests.Login dto) { return auth.login(dto); }

    @GetMapping(ApiPaths.ME)
    public Access.Principal me() { return Access.current(); }

    @PostMapping(ApiPaths.LOGOUT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = SecurityConstants.AUTHORIZATION, required = false) String header) {
        if (header != null && header.startsWith(SecurityConstants.BEARER_PREFIX))
            auth.logout(header.substring(SecurityConstants.BEARER_PREFIX.length()));
    }

    @PostMapping(ApiPaths.PASSWORD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthRequests.ChangePassword dto) { auth.changePassword(dto); }
}
