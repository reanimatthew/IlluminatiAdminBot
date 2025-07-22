package org.example.illuminatiadminbot.mapper;

import org.example.illuminatiadminbot.outbound.dto.GroupUserDto;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GroupUserMapper {
    GroupUserDto toGroupUserDto(GroupUser groupUser);
    GroupUser toGroupUserEntity(GroupUserDto groupUserDto);
}
