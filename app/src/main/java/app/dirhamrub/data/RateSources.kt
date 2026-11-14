package app.dirhamrub.data

import app.dirhamrub.domain.wallet.CountryGuides
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.UnionPayRates
import java.time.LocalDate

/** Replaceable I/O boundary; tests can deliver rates while real input continues without network. */
interface RateSources {
    suspend fun official(): CbrClient.Fetched?
    suspend fun onDate(date: LocalDate): OfficialRates?
    suspend fun unionPay(): UnionPayRates?
    suspend fun guide(): Pair<String, CountryGuides>?
}
internal object NetworkRateSources : RateSources {
    override suspend fun official() = CbrClient.fetch()
    override suspend fun onDate(date: LocalDate) = CbrClient.fetchOn(date)
    override suspend fun unionPay() = UnionPayClient.fetch()
    override suspend fun guide() = GuideClient.fetch()
}
