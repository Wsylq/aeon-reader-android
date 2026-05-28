package com.aeonreader.domain

sealed class AppError {
    data class NetworkError(val message: String, val cause: Throwable?) : AppError()
    data class ParseError(val message: String, val unrecognizedStructure: String?) : AppError()
    data class CacheError(val message: String) : AppError()
    data object OfflineError : AppError()
    data object NotFoundError : AppError()
}
