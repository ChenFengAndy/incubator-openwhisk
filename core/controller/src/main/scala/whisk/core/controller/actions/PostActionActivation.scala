/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.controller.actions

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import spray.http.StatusCodes.BadRequest
import spray.json._
import whisk.common.TransactionId
import whisk.core.controller.RejectRequest
import whisk.core.controller.WhiskServices
import whisk.core.entity._
import whisk.http.Messages

protected[core] trait PostActionActivation extends PrimitiveActions with SequenceActions {
    /** The core collections require backend services to be injected in this trait. */
    services: WhiskServices =>

    /**
     * Invokes an action which may be a sequence or a primitive (single) action.
     *
     * @param user the user posting the activation
     * @param action the action to activate (parameters for packaged actions must already be merged)
     * @param payload the parameters to pass to the action
     * @param waitForResponse if not empty, wait up to specified duration for a response (this is used for blocking activations)
     * @return a future that resolves with Left(activation id) when the request is queued, or Right(activation) for a blocking request
     *         which completes in time iff waiting for an response
     */
    protected[controller] def invokeAction(
        user: Identity,
        action: WhiskAction,
        payload: Option[JsObject],
        waitForResponse: Option[FiniteDuration],
        cause: Option[ActivationId])(
            implicit transid: TransactionId): Future[Either[ActivationId, WhiskActivation]] = {
        action.exec match {
            // this is a topmost sequence
            case SequenceExec(components) =>
                invokeSequence(user, action, components, payload, waitForResponse, cause, topmost = true, 0).map(r => r._1)
            case supportedExec if !supportedExec.deprecated =>
                invokeSingleAction(user, action, payload, waitForResponse, cause)
            case deprecatedExec =>
                Future.failed(RejectRequest(BadRequest, Messages.runtimeDeprecated(deprecatedExec)))
        }
    }
}
