/*
 * Copyright 2018-2020 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.application

import cats.Parallel
import cats.effect._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.buildtool.maven.MavenAlg
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.coursier.{CoursierAlg, VersionsCache}
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.git.GitAlg
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.{NurtureAlg, PullRequestRepository}
import org.scalasteward.core.persistence.JsonKeyValueStore
import org.scalasteward.core.repocache.{RefreshErrorAlg, RepoCacheAlg, RepoCacheRepository}
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafix.MigrationAlg
import org.scalasteward.core.scalafmt.ScalafmtAlg
import org.scalasteward.core.update.{FilterAlg, GroupMigrations, PruningAlg, UpdateAlg}
import org.scalasteward.core.util._
import org.scalasteward.core.util.uri._
import org.scalasteward.core.vcs.data.AuthenticatedUser
import org.scalasteward.core.vcs.{VCSApiAlg, VCSExtraAlg, VCSRepoAlg, VCSSelection}

object Context {
  def create[F[_]: ConcurrentEffect: ContextShift: Parallel: Timer](
      args: List[String]
  ): Resource[F, StewardAlg[F]] =
    for {
      blocker <- Blocker[F]
      cliArgs_ <- Resource.liftF(new Cli[F].parseArgs(args))
      implicit0(config: Config) <- Resource.liftF(Config.create[F](cliArgs_))
      implicit0(client: Client[F]) <- AsyncHttpClient.resource[F]()
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      implicit0(httpExistenceClient: HttpExistenceClient[F]) <- HttpExistenceClient.create[F]
      implicit0(user: AuthenticatedUser) <- Resource.liftF(config.vcsUser[F])
      implicit0(fileAlg: FileAlg[F]) = FileAlg.create[F]
      implicit0(migrationAlg: MigrationAlg) <- Resource.liftF(
        MigrationAlg.create[F](config.scalafixMigrations)
      )
      implicit0(groupMigration: GroupMigrations) <- Resource.liftF(GroupMigrations.create[F])
    } yield {
      val kvsPrefix = Some(config.vcsType.asString)
      implicit val dateTimeAlg: DateTimeAlg[F] = DateTimeAlg.create[F]
      implicit val processAlg: ProcessAlg[F] = ProcessAlg.create[F](blocker)
      implicit val workspaceAlg: WorkspaceAlg[F] = WorkspaceAlg.create[F]
      implicit val repoConfigAlg: RepoConfigAlg[F] = new RepoConfigAlg[F]
      implicit val filterAlg: FilterAlg[F] = new FilterAlg[F]
      implicit val gitAlg: GitAlg[F] = GitAlg.create[F]
      implicit val httpJsonClient: HttpJsonClient[F] = new HttpJsonClient[F]
      implicit val repoCacheRepository: RepoCacheRepository[F] =
        new RepoCacheRepository[F](new JsonKeyValueStore("repo_cache", "1", kvsPrefix))
      implicit val selfCheckAlg: SelfCheckAlg[F] = new SelfCheckAlg[F]
      val vcsSelection = new VCSSelection[F]
      implicit val vcsApiAlg: VCSApiAlg[F] = vcsSelection.getAlg(config)
      implicit val vcsRepoAlg: VCSRepoAlg[F] = VCSRepoAlg.create[F](config, gitAlg)
      implicit val vcsExtraAlg: VCSExtraAlg[F] = VCSExtraAlg.create[F]
      implicit val pullRequestRepository: PullRequestRepository[F] =
        new PullRequestRepository[F](new JsonKeyValueStore("pull_requests", "2", kvsPrefix))
      implicit val scalafmtAlg: ScalafmtAlg[F] = ScalafmtAlg.create[F]
      implicit val coursierAlg: CoursierAlg[F] = CoursierAlg.create[F]
      implicit val versionsCache: VersionsCache[F] =
        new VersionsCache[F](config.cacheTtl, new JsonKeyValueStore("versions", "2"))
      implicit val updateAlg: UpdateAlg[F] = new UpdateAlg[F]
      implicit val mavenAlg: MavenAlg[F] = MavenAlg.create[F]
      implicit val sbtAlg: SbtAlg[F] = SbtAlg.create[F]
      implicit val buildToolDispatcher: BuildToolDispatcher[F] = BuildToolDispatcher.create[F]
      implicit val refreshErrorAlg: RefreshErrorAlg[F] =
        new RefreshErrorAlg[F](new JsonKeyValueStore("refresh_error", "1", kvsPrefix))
      implicit val repoCacheAlg: RepoCacheAlg[F] = new RepoCacheAlg[F]
      implicit val editAlg: EditAlg[F] = new EditAlg[F]
      implicit val nurtureAlg: NurtureAlg[F] = new NurtureAlg[F]
      implicit val pruningAlg: PruningAlg[F] = new PruningAlg[F]
      new StewardAlg[F]
    }
}
