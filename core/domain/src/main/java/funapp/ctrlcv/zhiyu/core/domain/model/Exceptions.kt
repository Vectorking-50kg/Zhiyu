package funapp.ctrlcv.zhiyu.core.domain.model

class NoCookieException(val platform: Platform) : Exception("No cookie for ${platform.displayName}")
class SessionExpiredException(val platform: Platform) : Exception("Session expired for ${platform.displayName}")
class ApiStructureChangedException(val platform: Platform, message: String) : Exception(message)
class RateLimitException(val platform: Platform) : Exception("Rate limited for ${platform.displayName}")
