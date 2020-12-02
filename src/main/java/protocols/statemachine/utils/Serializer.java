package protocols.statemachine.utils;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public interface Serializer<T>{

    void serialize(T t, ByteBuf buf);z

    T deserialize(ByteBuf buf);

}
