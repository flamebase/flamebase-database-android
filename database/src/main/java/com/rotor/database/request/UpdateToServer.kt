package com.rotor.database.request

/**
 * Created by efraespada on 11/03/2018.
 */

data class UpdateToServer( val method: String,
                           val database: String,
                           val path: String,
                           val token: String,
                           val os: String,
                           val differences: String,
                           val clean: Boolean)