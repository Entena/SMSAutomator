from fastapi import APIRouter, Depends
from predictor import SMSFilterPredictor, SMSFilterPrediction
from ..dependencies import (
    get_sms_filter_predictor,
)
from ..schemas import SMSFilterRequest

router = APIRouter()


@router.post(
    "/filter/sms",
    summary="Filter SMS",
    response_model=SMSFilterPrediction,
)
async def filter_sms(
    request: SMSFilterRequest,
    predictor: SMSFilterPredictor = Depends(get_sms_filter_predictor),
) -> SMSFilterPrediction:
    return await predictor.predict(request.sms)
