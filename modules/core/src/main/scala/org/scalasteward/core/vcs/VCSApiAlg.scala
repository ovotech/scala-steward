/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core.vcs

import cats.{ApplicativeThrow, MonadThrow}
import cats.syntax.all._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.vcs.data._

trait VCSApiAlg[F[_]] {
  def createFork(repo: Repo): F[RepoOut]

  def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut]

  def closePullRequest(repo: Repo, number: PullRequestNumber): F[PullRequestOut]

  def getBranch(repo: Repo, branch: Branch): F[BranchOut]

  def getRepo(repo: Repo): F[RepoOut]

  def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]]

  def referencePullRequest(number: PullRequestNumber): String =
    s"#${number.value}"

  def commentPullRequest(repo: Repo, number: PullRequestNumber, comment: String): F[Comment]

  final def createForkOrGetRepo(repo: Repo, doNotFork: Boolean): F[RepoOut] =
    if (doNotFork) getRepo(repo) else createFork(repo)

  final def createForkOrGetRepoWithDefaultBranch(repo: Repo, doNotFork: Boolean)(implicit
      F: MonadThrow[F]
  ): F[(RepoOut, BranchOut)] =
    for {
      forkOrRepo <- createForkOrGetRepo(repo, doNotFork)
      defaultBranch <- getDefaultBranchOfParentOrRepo(forkOrRepo, doNotFork)
    } yield (forkOrRepo, defaultBranch)

  final def getDefaultBranchOfParentOrRepo(repoOut: RepoOut, doNotFork: Boolean)(implicit
      F: MonadThrow[F]
  ): F[BranchOut] =
    parentOrRepo(repoOut, doNotFork).flatMap(getDefaultBranch)

  final def parentOrRepo(repoOut: RepoOut, doNotFork: Boolean)(implicit
      F: ApplicativeThrow[F]
  ): F[RepoOut] =
    if (doNotFork) F.pure(repoOut) else repoOut.parentOrRaise[F]

  private def getDefaultBranch(repoOut: RepoOut): F[BranchOut] =
    getBranch(repoOut.repo, repoOut.default_branch)
}
