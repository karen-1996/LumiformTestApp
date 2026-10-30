package com.lumiformtest.form.data.remote

import java.io.IOException

internal class NoInternetConnectionException(cause: IOException) : IOException("No internet connection", cause)
