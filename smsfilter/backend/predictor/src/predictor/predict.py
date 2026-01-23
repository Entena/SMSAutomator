import asyncio
from transformers import AutoTokenizer
from llama_cpp import Llama
from settings import Settings
from .schemas import SMSFilterPrediction


class SMSFilterPredictor:
    def __init__(self, tokenizer: AutoTokenizer, model: Llama, settings: Settings):
        self.settings = settings
        self.tokenizer = tokenizer
        self.model = model
        self.categories = {
            f"S{i + 1}": cat
            for i, cat in enumerate(
                [
                    "VIOLENT_CRIMES",
                    "NONVIOLENT_CRIMES",
                    "SEX_RELATED_CRIMES",
                    "CHILD_SEXUAL_EXPLOITATION",
                    "DEFAMATION",
                    "SPECIALIZED_ADVICE",
                    "PRIVACY",
                    "INTELLECTUAL_PROPERTY",
                    "INDISCRIMINATE_WEAPONS",
                    "HATE",
                    "SUICIDE_AND_SELF_HARM",
                    "SEXUAL_CONTENT",
                    "ELECTIONS",
                ]
            )
        }
        self.exclude_keys = [
            k for k, v in self.categories.items() if getattr(self.settings, v) is False
        ]
        self.exclude = [self.categories[k] for k in self.exclude_keys]
        self.include = [
            self.categories[k]
            for k, v in self.categories.items()
            if getattr(self.settings, v) is True
        ] + ["UNKNOWN"]
        self.lock = asyncio.Lock()

    async def predict(self, sms: str) -> SMSFilterPrediction:
        conversation = [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": sms,
                    }
                ],
            }
        ]
        loop = asyncio.get_running_loop()
        async with self.lock:

            def _inference():
                prompt = self.tokenizer.apply_chat_template(
                    conversation,
                    excluded_category_keys=self.exclude_keys,
                    tokenize=False,
                    add_generation_prompt=True,
                )
                completion = self.model(
                    prompt,
                    temperature=0.0,
                    stop=["<|eot_id|>"],
                )
                return completion["choices"][0]["text"].strip()

            result = await loop.run_in_executor(None, _inference)
        if "unsafe" in result:
            try:
                cat_key = result.split("unsafe")[1].strip()
                cat = self.categories.get(cat_key, "UNKNOWN")
            except IndexError:
                cat = "UNKNOWN"

            return SMSFilterPrediction(
                blocked=True,
                reason=cat,
                included_categories=self.include,
                excluded_categories=self.exclude,
            )
        else:
            return SMSFilterPrediction(
                blocked=False,
                reason=None,
                included_categories=self.include,
                excluded_categories=self.exclude,
            )
