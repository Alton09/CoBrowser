package com.example.cobrowser

import com.twilio.video.Room

sealed class RoomEvent {
    data class ConnectedEvent(val room: Room): RoomEvent()
    data class ReconnectedEvent(val room: Room): RoomEvent()
    data class ReconnectingEvent(val room: Room): RoomEvent()
    class ConnectFailureEvent: RoomEvent()
    class DisconnectedEvent: RoomEvent()
    class ParticipantConnectedEvent: RoomEvent()
    class ParticipantDisconnectedEvent: RoomEvent()
}