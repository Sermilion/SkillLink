package skilllink.application.ports

interface DiagnosticsPort {
  fun record(
    eventCode: String,
    managedId: String?,
    detail: String,
  )

  fun reportSecondaryFailure(
    primaryCode: String,
    secondaryCode: String,
    detail: String,
  )
}
