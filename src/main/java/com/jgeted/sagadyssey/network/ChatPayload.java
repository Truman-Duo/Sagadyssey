package com.jgeted.sagadyssey.network;

import com.jgeted.sagadyssey.Sagadyssey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 从客户端发送到服务端的数据包。
 * 服务端收到后向玩家发送聊天消息。
 */
public record ChatPayload(String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Sagadyssey.MOD_ID, "chat_message"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChatPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ChatPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new ChatPayload(buffer.readUtf(256));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ChatPayload value) {
                    buffer.writeUtf(value.message, 256);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
