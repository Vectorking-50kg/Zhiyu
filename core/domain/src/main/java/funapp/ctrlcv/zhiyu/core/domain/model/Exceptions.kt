package funapp.ctrlcv.zhiyu.core.domain.model

class NoCookieException(val platform: Platform) : Exception("No cookie for ${platform.displayName}")
class SessionExpiredException(val platform: Platform) : Exception("Session expired for ${platform.displayName}")
class ApiStructureChangedException(val platform: Platform, message: String) : Exception(message)
class RateLimitException(val platform: Platform, val retryAfterSeconds: Long? = null) : Exception("请求过于频繁，请稍后重试")
class PermissionDeniedException(val platform: Platform) : Exception("暂时无法访问，请检查账号权限或稍后重试")
class UsageRequestException(val platform: Platform, val failure: UsageFailure) : Exception(failure.message)
