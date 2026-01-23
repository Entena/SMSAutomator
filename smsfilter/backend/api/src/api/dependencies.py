from fastapi import Request, Depends
from transformers import AutoTokenizer
from llama_cpp import Llama
from predictor import SMSFilterPredictor
from settings import get_settings, Settings
from .schemas import SMSFilterRequest


def get_request_settings(request: SMSFilterRequest) -> Settings:
    update_data = {
        "VIOLENT_CRIMES": request.violent_crimes,
        "NONVIOLENT_CRIMES": request.nonviolent_crimes,
        "SEX_RELATED_CRIMES": request.sex_related_crimes,
        "CHILD_SEXUAL_EXPLOITATION": request.child_sexual_exploitation,
        "DEFAMATION": request.defamation,
        "SPECIALIZED_ADVICE": request.specialized_advice,
        "PRIVACY": request.privacy,
        "INTELLECTUAL_PROPERTY": request.intellectual_property,
        "INDISCRIMINATE_WEAPONS": request.indiscriminate_weapons,
        "HATE": request.hate,
        "SUICIDE_AND_SELF_HARM": request.suicide_and_self_harm,
        "SEXUAL_CONTENT": request.sexual_content,
        "ELECTIONS": request.elections,
    }
    filtered_updates = {k: v for k, v in update_data.items() if v is not None}
    return get_settings().model_copy(deep=True, update=filtered_updates)


def get_model(request: Request) -> Llama:
    return request.app.state.model


def get_tokenizer(request: Request) -> AutoTokenizer:
    return request.app.state.tokenizer


def get_sms_filter_predictor(
    tokenizer: AutoTokenizer = Depends(get_tokenizer),
    model: Llama = Depends(get_model),
    request_settings: Settings = Depends(get_request_settings),
) -> SMSFilterPredictor:
    return SMSFilterPredictor(
        tokenizer=tokenizer,
        model=model,
        settings=request_settings,
    )
