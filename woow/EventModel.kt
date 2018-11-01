package company.project.core

import android.annotation.SuppressLint
import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.rx.RxApollo
import dev.klippe.karma.*
import dev.klippe.karma.dataobjects.*
import dev.klippe.karma.type.EventGeoPointInput
import dev.klippe.karma.type.EventInput
import dev.klippe.karma.type.EventPackageInput
import rx.Observable
import java.text.SimpleDateFormat

class EventModel(
        private val mApolloClient: ApolloClient,
        private val mAuthModel: AuthModel
) {
    @SuppressLint("SimpleDateFormat")
    fun findDrivers(
            latSouthWest: Double, lngSouthWest: Double,
            latNorthEast: Double, lngNorthEast: Double
    ): Observable<List<SearchDriver>> =
            mApolloClient.query(
                    GetEventsByTypeQuery.builder()
                            .type("Trip")
                            .latA(latSouthWest)
                            .lngA(lngSouthWest)
                            .latB(latNorthEast)
                            .lngB(lngNorthEast)
                            .build()
            )
                    .let { RxApollo.from(it) }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .concatMap { Observable.from(it.data()!!.eventsByType()) }
                    .map { SearchDriver(it.fragments().eventFragment()) }
                    .toList()

    fun findDriver(eventId: String): Observable<SearchDriver> =
            mApolloClient.query(
                    GetEventByIdQuery.builder()
                            .id(eventId)
                            .build()
            )
                    .let { RxApollo.from(it) }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .map { SearchDriver(it.data()!!.event()!!.fragments().eventFragment()) }

    fun findSenders(
            latSouthWest: Double, lngSouthWest: Double,
            latNorthEast: Double, lngNorthEast: Double
    ): Observable<List<SearchSender>> =
            mApolloClient.query(
                    GetEventsByTypeQuery.builder()
                            .type("SenderSearch")
                            .latA(latSouthWest)
                            .lngA(lngSouthWest)
                            .latB(latNorthEast)
                            .lngB(lngNorthEast)
                            .build()
            )
                    .let { RxApollo.from(it) }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .concatMap { Observable.from(it.data()!!.eventsByType()) }
                    .map { SearchSender(it.fragments().eventFragment()) }
                    .toList()

    fun findSender(eventId: String): Observable<SearchSender> =
            mApolloClient.query(
                    GetEventByIdQuery.builder()
                            .id(eventId)
                            .build()
            )
                    .let { RxApollo.from(it) }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .map { SearchSender(it.data()!!.event()!!.fragments().eventFragment()) }


    fun findPassengers(
            latSouthWest: Double, lngSouthWest: Double,
            latNorthEast: Double, lngNorthEast: Double
    ): Observable<List<SearchPassenger>> =
            mApolloClient.query(
                    GetEventsByTypeQuery.builder()
                            .type("PassengerSearch")
                            .latA(latSouthWest)
                            .lngA(lngSouthWest)
                            .latB(latNorthEast)
                            .lngB(lngNorthEast)
                            .build()
            )
                    .let { RxApollo.from(it) }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .concatMap { Observable.from(it.data()!!.eventsByType()) }
                    .map { SearchPassenger(it.fragments().eventFragment()) }
                    .toList()

    fun findPassenger(eventId: String): Observable<SearchPassenger> =
            mApolloClient.query(
                    GetEventByIdQuery.builder()
                            .id(eventId)
                            .build()
            )
                    .let { RxApollo.from(it) }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .map { SearchPassenger(it.data()!!.event()!!.fragments().eventFragment()) }


    @SuppressLint("SimpleDateFormat")
    fun createTripEvent(createTripEventData: CreateTripEventData): Observable<String> {
        val path = mutableListOf<EventGeoPointInput>()

        if (createTripEventData.path.size < 2) {
            throw RuntimeException("Invalid argument exception. path.size < 2")
        }

        path.add(EventGeoPointInput.builder()
                .lat(createTripEventData.path[0].lat)
                .lng(createTripEventData.path[0].lng)
                .address(createTripEventData.path[0].address)
                .num(0)
                .build()
        )

        path.add(EventGeoPointInput.builder()
                .lat(createTripEventData.path[1].lat)
                .lng(createTripEventData.path[1].lng)
                .address(createTripEventData.path[1].address)
                .num(1)
                .build()
        )

        Log.d("LOGI", "StartTime before => ${createTripEventData.startDate}")
        val startTime = SimpleDateFormat("yyyy-MM-dd hh:mm:ss ZZ").format(
                SimpleDateFormat("dd/MM/yyyy hh:mm").parse(createTripEventData.startDate)
        )
        Log.d("LOGI", "StartTime after => $startTime")

        val eventInput = EventInput.builder()
                .type("Trip")
                .startDate(startTime)
                .path(path)
                .eventPackages(emptyList())
                .seatCount(createTripEventData.freePlacesCount)
                .lat(createTripEventData.path[0].lat)
                .lng(createTripEventData.path[0].lng)
                .address(createTripEventData.path[0].address)
                .build()

        return mApolloClient.mutate(
                CreateEventMutation.builder()
                        .eventInput(eventInput)
                        .build()
        ).let { RxApollo.from(it) }
                .map { it.data()?.createEvent()?.Id() ?: throw RuntimeException("Id cannot be null") }
    }

    @SuppressLint("SimpleDateFormat")
    fun createSenderSearchEvent(createSentEventData: CreateSenderSearchEventData): Observable<String> {
        val path = mutableListOf<EventGeoPointInput>()

        if (createSentEventData.path.size < 2) {
            throw RuntimeException("Invalid argument exception. path.size < 2")
        }

        path.add(EventGeoPointInput.builder()
                .lat(createSentEventData.path[0].lat)
                .lng(createSentEventData.path[0].lng)
                .address(createSentEventData.path[0].address)
                .num(0)
                .build()
        )

        path.add(EventGeoPointInput.builder()
                .lat(createSentEventData.path[1].lat)
                .lng(createSentEventData.path[1].lng)
                .address(createSentEventData.path[1].address)
                .num(1)
                .build()
        )

        Log.d("LOGI", "StartTime before => ${createSentEventData.startDate}")
        val startTime = SimpleDateFormat("yyyy-MM-dd hh:mm:ss ZZ").format(
                SimpleDateFormat("dd/MM/yyyy hh:mm").parse(createSentEventData.startDate)
        )
        Log.d("LOGI", "StartTime after => $startTime")

        val eventPackageInput = EventPackageInput.builder()
                .dimK(createSentEventData.dimK)
                .dimName(createSentEventData.dimName)
                .valueK(createSentEventData.valueK)
                .build()

        val eventInput = EventInput.builder()
                .type("SenderSearch")
                .startDate(startTime)
                .path(path)
                .eventPackages(listOf(eventPackageInput))
                .lat(createSentEventData.path[0].lat)
                .lng(createSentEventData.path[0].lng)
                .address(createSentEventData.path[0].address)
                .build()

        return mApolloClient.mutate(
                CreateEventMutation.builder()
                        .eventInput(eventInput)
                        .build()
        ).let { RxApollo.from(it) }
                .map {
                    it.data()?.createEvent()?.Id() ?: throw RuntimeException("Id cannot be null")
                }
    }

    @SuppressLint("SimpleDateFormat")
    fun createPassengerSearch(createTripEventData: CreatePassengerEventData): Observable<String> {
        val path = mutableListOf<EventGeoPointInput>()

        if (createTripEventData.path.size < 2) {
            throw RuntimeException("Invalid argument exception. path.size < 2")
        }

        path.add(EventGeoPointInput.builder()
                .lat(createTripEventData.path[0].lat)
                .lng(createTripEventData.path[0].lng)
                .address(createTripEventData.path[0].address)
                .num(0)
                .build()
        )

        path.add(EventGeoPointInput.builder()
                .lat(createTripEventData.path[1].lat)
                .lng(createTripEventData.path[1].lng)
                .address(createTripEventData.path[1].address)
                .num(1)
                .build()
        )

        Log.d("LOGI", "StartTime before => ${createTripEventData.startDate}")
        val startTime = SimpleDateFormat("yyyy-MM-dd hh:mm:ss ZZ").format(
                SimpleDateFormat("dd/MM/yyyy hh:mm").parse(createTripEventData.startDate)
        )
        Log.d("LOG", "StartTime after => $startTime")

        val eventInput = EventInput.builder()
                .type("PassengerSearch")
                .startDate(startTime)
                .path(path)
                .eventPackages(emptyList())
                .passengerCount(createTripEventData.peopleCount)
                .baggage(createTripEventData.baggage)
                .lat(createTripEventData.path[0].lat)
                .lng(createTripEventData.path[0].lng)
                .address(createTripEventData.path[0].address)
                .build()

        return mApolloClient.mutate(
                CreateEventMutation.builder()
                        .eventInput(eventInput)
                        .build()
        ).let { RxApollo.from(it) }
                .map {
                    it.data()?.createEvent()?.Id() ?: throw RuntimeException("Id cannot be null")
                }
    }

    @Suppress("IMPLICIT_CAST_TO_ANY")
    fun userEvents(): Observable<List<Any>> =
            mApolloClient.query(
                    GetMyEventsQuery.builder()
                            .build()
            )
                    .let { RxApollo.from(it) }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .concatMap { Observable.from(it.data()!!.userEvents()) }
                    .map {
                        when (it.fragments().eventFragment().Type()) {
                            "Trip" -> SearchDriver(it.fragments().eventFragment())
                            "SenderSearch" -> SearchSender(it.fragments().eventFragment())
                            "PassengerSearch" -> SearchPassenger(it.fragments().eventFragment())
                            else -> UserEvent(it.fragments().eventFragment())
                        }
                    }
                    .toList()

    fun startEvent(eventId: String): Observable<Boolean> =
            mApolloClient.mutate(StartEventMutation.builder().address("").lat(0.0).lng(0.0).eventId(eventId).build()).let {
                RxApollo.from(it)
            }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .map { true }

    fun cancelEvent(eventId: String): Observable<Boolean> =
            mApolloClient.mutate(CancelEventMutation.builder().address("").lat(0.0).lng(0.0).eventId(eventId).build()).let {
                RxApollo.from(it)
            }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .map { true }

    fun finishEvent(eventId: String): Observable<Boolean> =
            mApolloClient.mutate(FinishEventMutation.builder().address("").lat(0.0).lng(0.0).eventId(eventId).build()).let {
                RxApollo.from(it)
            }
                    .doOnNext { if (it.hasErrors()) throw RuntimeException("Graphql error: ${it.errors().joinToString { ", " }}") }
                    .map { true }
}
