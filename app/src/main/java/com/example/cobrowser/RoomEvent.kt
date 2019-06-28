package com.example.cobrowser

import com.twilio.video.RemoteParticipant
import com.twilio.video.Room

sealed class RoomEvent {
    data class ConnectedEvent(val room: Room): RoomEvent()
    data class ReconnectedEvent(val room: Room): RoomEvent()
    data class ReconnectingEvent(val room: Room): RoomEvent()
    data class ParticipantConnectedEvent(val participant: RemoteParticipant): RoomEvent()
    data class ParticipantDisconnectedEvent(val participant: RemoteParticipant): RoomEvent()
    class ExitRoom() : RoomEvent()
    class ConnectFailureEvent: RoomEvent()
    class DisconnectedEvent: RoomEvent()
}