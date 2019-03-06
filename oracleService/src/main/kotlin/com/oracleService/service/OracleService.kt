package com.oracleService.service

import net.corda.core.crypto.newSecureRandom
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class OracleService(val services: AppServiceHub): SingletonSerializeAsToken() {

    fun getRandomDiceRoll(): Int {
        return newSecureRandom().nextInt(6) + 1
    }

}