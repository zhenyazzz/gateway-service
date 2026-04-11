package com.innowise.gateway.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.innowise.gateway.dto.iternal.AuthRegisterRequest;
import com.innowise.gateway.dto.iternal.UserCreateRequest;
import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.dto.response.RegisterGatewayResponse;
import com.innowise.gateway.dto.response.RegisterResponse;
import com.innowise.gateway.dto.response.UserResponse;

@Mapper(componentModel = "spring")
public interface RegisterRequestMapper {

    AuthRegisterRequest toAuthRegisterRequest(RegisterRequest request);

    UserCreateRequest toUserCreateRequest(UUID userId, RegisterRequest request);

    @Mapping(target = "userId", source = "registerResponse.user.id")
    @Mapping(target = "email", source = "userResponse.email")
    @Mapping(target = "name", source = "userResponse.name")
    @Mapping(target = "surname", source = "userResponse.surname")
    @Mapping(target = "roles", source = "registerResponse.user.roles")
    @Mapping(target = "accessToken", source = "registerResponse.accessToken")
    @Mapping(target = "refreshToken", source = "registerResponse.refreshToken")
    @Mapping(target = "expiresIn", source = "registerResponse.expiresIn")
    RegisterGatewayResponse toRegisterGatewayResponse(RegisterResponse registerResponse, UserResponse userResponse);
}
