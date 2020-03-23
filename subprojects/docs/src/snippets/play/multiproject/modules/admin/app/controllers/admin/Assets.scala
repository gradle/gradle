package controllers.admin

import javax.inject._

import play.api.http.HttpErrorHandler

// https://www.playframework.com/documentation/2.6.x/SBTSubProjects
class Assets @Inject() (
  errorHandler: HttpErrorHandler,
  assetsMetadata: controllers.AssetsMetadata
) extends controllers.AssetsBuilder(errorHandler, assetsMetadata)
