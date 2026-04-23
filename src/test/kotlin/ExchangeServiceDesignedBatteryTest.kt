package com.example.exchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import org.iesra.revilofe.ExchangeRateProvider
import org.iesra.revilofe.ExchangeService
import org.iesra.revilofe.InMemoryExchangeRateProvider
import org.iesra.revilofe.Money

class ExchangeServiceDesignedBatteryTest : DescribeSpec({

    afterTest {
        clearAllMocks()
    }

    describe("battery designed from equivalence classes for ExchangeService") {

        describe("validación de entrada") {

            val provider = mockk<ExchangeRateProvider>(relaxed = true)
            val service = ExchangeService(provider)

            it("lanza excepción si la cantidad es 0") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(0, "USD"), "EUR")
                }
            }

            it("lanza excepción si la cantidad es negativa") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(-10, "USD"), "EUR")
                }
            }

            it("lanza excepción si moneda origen no tiene 3 letras") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "US"), "EUR")
                }
            }

            it("lanza excepción si moneda destino no tiene 3 letras") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "USD"), "EURO")
                }
            }
        }
        describe("misma moneda") {

            it("devuelve misma cantidad sin consultar proveedor") {

                val real = InMemoryExchangeRateProvider(emptyMap())
                val spy = spyk(real)

                val service = ExchangeService(spy)

                val result = service.exchange(Money(1000, "USD"), "USD")

                result shouldBe 1000

                verify(exactly = 0) { spy.rate(any()) }
            }
        }
        describe("conversión directa") {

            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(provider)

            it("convierte correctamente con tasa directa") {

                every { provider.rate("USDEUR") } returns 0.92

                val result = service.exchange(Money(1000, "USD"), "EUR")

                result shouldBe 920

                verify(exactly = 1) { provider.rate("USDEUR") }
            }
        }
        describe("conversión directa") {

            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(provider)

            it("convierte correctamente con tasa directa") {

                every { provider.rate("USDEUR") } returns 0.92

                val result = service.exchange(Money(1000, "USD"), "EUR")

                result shouldBe 920

                verify(exactly = 1) { provider.rate("USDEUR") }
            }
        }
        describe("conversión cruzada") {

            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(
                provider,
                supportedCurrencies = setOf("USD", "EUR", "GBP", "JPY")
            )

            it("usa cruce cuando no hay tasa directa") {

                every { provider.rate("USDJPY") } throws IllegalArgumentException()
                every { provider.rate("USDGBP") } returns 0.8
                every { provider.rate("GBPJPY") } returns 150.0

                val result = service.exchange(Money(100, "USD"), "JPY")

                result shouldBe (100 * 0.8 * 150).toLong()
            }
            it("usa segunda ruta intermedia si la primera falla") {

                every { provider.rate("USDJPY") } throws IllegalArgumentException()
                every { provider.rate("USDGBP") } throws IllegalArgumentException()
                every { provider.rate("USDEUR") } returns 0.9
                every { provider.rate("EURJPY") } returns 150.0

                val result = service.exchange(Money(100, "USD"), "JPY")

                result shouldBe (100 * 0.9 * 150).toLong()
            }
        }
        describe("sin ruta válida") {

            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(
                provider,
                supportedCurrencies = setOf("USD", "EUR", "GBP", "JPY")
            )

            it("lanza excepción si no existe conversión") {

                every { provider.rate(any()) } throws IllegalArgumentException()

                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "USD"), "JPY")
                }
            }
            it("verifica orden de llamadas en cruce") {

                every { provider.rate("USDJPY") } throws IllegalArgumentException()
                every { provider.rate("USDGBP") } returns 0.8
                every { provider.rate("GBPJPY") } returns 150.0

                service.exchange(Money(100, "USD"), "JPY")

                verifySequence {
                    provider.rate("USDJPY")
                    provider.rate("USDGBP")
                    provider.rate("GBPJPY")
                }
            }
        }

}})
