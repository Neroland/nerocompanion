package za.co.neroland.nerocompanion.network;

import net.minecraft.network.chat.Component;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/** Renders a {@link Payloads.Feedback} as a component: arguments starting with {@code @} are lang keys. */
public final class Feedbacks {

    private Feedbacks() {
    }

    public static Component component(Payloads.Feedback feedback) {
        Object[] args = new Object[feedback.args().size()];
        for (int i = 0; i < args.length; i++) {
            String arg = feedback.args().get(i);
            args[i] = arg.startsWith("@") ? Component.translatable(arg.substring(1)) : Component.literal(arg);
        }
        return Component.translatable(feedback.key(), args);
    }
}
