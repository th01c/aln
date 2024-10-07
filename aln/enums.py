from .Capabilites import Capabilites

class enums:
    NOISE_CANCELLATION = Capabilites.NOISE_CANCELLATION
    CONVERSATION_AWARENESS = Capabilites.CONVERSATION_AWARENESS
    CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY = Capabilites.CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY
    PREFIX = b'\x04\x00\x04\x00'
    SETTINGS = b"\x09\x00"
    SUFFIX = b'\x00\x00\x00'
    NOTIFICATION_FILTER = b'\x0f'
    SPECIFIC_FEATURES = b'\x4d'
    SET_SPECIFIC_FEATURES = PREFIX + SPECIFIC_FEATURES + b"\x00\xff\x00\x00\x00\x00\x00\x00\x00"
    REQUEST_NOTIFICATIONS = PREFIX + NOTIFICATION_FILTER + b"\x00\xff\xff\xff\xff"
    HANDSHAKE = b"\x00\x00\x04\x00\x01\x00\x02\x00\x00\x00\x00\x00\x00\x00\x00\x00"
    NOISE_CANCELLATION_PREFIX = PREFIX + SETTINGS + NOISE_CANCELLATION
    NOISE_CANCELLATION_OFF = NOISE_CANCELLATION_PREFIX + Capabilites.NoiseCancellation.OFF + SUFFIX
    NOISE_CANCELLATION_ON = NOISE_CANCELLATION_PREFIX + Capabilites.NoiseCancellation.ON + SUFFIX
    NOISE_CANCELLATION_TRANSPARENCY = NOISE_CANCELLATION_PREFIX + Capabilites.NoiseCancellation.TRANSPARENCY + SUFFIX
    NOISE_CANCELLATION_ADAPTIVE = NOISE_CANCELLATION_PREFIX + Capabilites.NoiseCancellation.ADAPTIVE + SUFFIX
    
    SET_CONVERSATION_AWARENESS_OFF = PREFIX + SETTINGS + CONVERSATION_AWARENESS + Capabilites.ConversationAwareness.OFF + SUFFIX
    SET_CONVERSATION_AWARENESS_ON = PREFIX + SETTINGS + CONVERSATION_AWARENESS + Capabilites.ConversationAwareness.ON + SUFFIX
    
    CONVERSATION_AWARENESS_RECEIVE_PREFIX = PREFIX + b"\x4b\x00\x02\00"